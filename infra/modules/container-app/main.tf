terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

resource "azurerm_container_app" "this" {
  name                         = var.name
  container_app_environment_id = var.environment_id
  resource_group_name          = var.resource_group_name
  revision_mode                = "Single"
  # Without a profile the platform treats the app as an "express" app, which rejects TCP ingress and
  # Key Vault secret references.
  workload_profile_name = "Consumption"
  tags                  = var.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [var.identity_id]
  }

  dynamic "secret" {
    for_each = var.secrets

    content {
      name                = secret.key
      key_vault_secret_id = secret.value
      identity            = var.identity_id
    }
  }

  template {
    min_replicas = var.min_replicas
    max_replicas = var.max_replicas

    # A "Secret" volume exposes every secret of the app as a file named after the secret. The
    # services read <dir>/<name>/<name>.crt, so the same volume is mounted once per folder.
    dynamic "volume" {
      for_each = length(var.secret_mount_paths) == 0 ? [] : [1]

      content {
        name         = "secrets"
        storage_type = "Secret"
      }
    }

    container {
      name   = var.name
      image  = var.image
      cpu    = var.cpu
      memory = var.memory

      dynamic "env" {
        for_each = var.env

        content {
          name  = env.key
          value = env.value
        }
      }

      dynamic "env" {
        for_each = var.secret_env

        content {
          name        = env.key
          secret_name = env.value
        }
      }

      dynamic "volume_mounts" {
        for_each = toset(var.secret_mount_paths)

        content {
          name = "secrets"
          path = volume_mounts.value
        }
      }

      dynamic "readiness_probe" {
        for_each = var.probe == null ? [] : [var.probe]

        content {
          transport = readiness_probe.value.path == null ? "TCP" : "HTTP"
          port      = readiness_probe.value.port
          path      = readiness_probe.value.path

          initial_delay           = readiness_probe.value.initial_delay_seconds
          interval_seconds        = 10
          timeout                 = 5
          failure_count_threshold = 10
        }
      }

      dynamic "startup_probe" {
        for_each = var.probe == null ? [] : [var.probe]

        content {
          transport = startup_probe.value.path == null ? "TCP" : "HTTP"
          port      = startup_probe.value.port
          path      = startup_probe.value.path

          interval_seconds        = 10
          timeout                 = 5
          failure_count_threshold = 30
        }
      }
    }
  }

  dynamic "ingress" {
    for_each = var.ingress == null ? [] : [var.ingress]

    content {
      external_enabled = ingress.value.external
      target_port      = ingress.value.target_port
      transport        = ingress.value.transport

      traffic_weight {
        percentage      = 100
        latest_revision = true
      }
    }
  }
}
