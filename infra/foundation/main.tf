data "azurerm_subscription" "current" {}

data "azurerm_client_config" "current" {}

locals {
  tags = {
    project = var.project
    env     = "foundation"
    owner   = var.owner
    ttl     = "persistent"
  }
}

resource "azurerm_resource_group" "foundation" {
  name     = "rg-${var.project}-foundation"
  location = var.location
  tags     = local.tags
}

# Empty on purpose: it costs nothing, and the dev layer creates and destroys everything inside it.
resource "azurerm_resource_group" "dev" {
  name     = "rg-${var.project}-dev"
  location = var.location
  tags     = merge(local.tags, { env = "dev", ttl = "per-window" })
}

# --- Remote state ------------------------------------------------------------------------------

resource "random_string" "state_suffix" {
  length  = 6
  upper   = false
  special = false
}

resource "azurerm_storage_account" "state" {
  name                     = "st${var.project}tf${random_string.state_suffix.result}"
  resource_group_name      = azurerm_resource_group.foundation.name
  location                 = azurerm_resource_group.foundation.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  account_kind             = "StorageV2"

  min_tls_version                 = "TLS1_2"
  https_traffic_only_enabled      = true
  allow_nested_items_to_be_public = false

  # The backend authenticates with Entra ID (use_azuread_auth). Shared keys stay enabled only so
  # this layer can create the container with the standard provider; nothing else uses them.
  shared_access_key_enabled = true

  blob_properties {
    versioning_enabled = true
    delete_retention_policy {
      days = 14
    }
  }

  tags = local.tags
}

resource "azurerm_storage_container" "state" {
  name                  = "tfstate"
  storage_account_id    = azurerm_storage_account.state.id
  container_access_type = "private"
}

# --- Identity used by GitHub Actions (OIDC, no stored secret) -----------------------------------

resource "azurerm_user_assigned_identity" "github" {
  name                = "id-${var.project}-github"
  resource_group_name = azurerm_resource_group.foundation.name
  location            = azurerm_resource_group.foundation.location
  tags                = local.tags
}

# Only jobs that run under the protected environment can obtain a token, so the environment
# approval is the gate for every change to Azure.
resource "azurerm_federated_identity_credential" "github_environment" {
  name                = "github-${var.github_environment}"
  resource_group_name = azurerm_resource_group.foundation.name
  parent_id           = azurerm_user_assigned_identity.github.id
  audience            = ["api://AzureADTokenExchange"]
  issuer              = "https://token.actions.githubusercontent.com"
  subject             = "repo:${coalesce(var.github_oidc_repository, var.github_repository)}:environment:${var.github_environment}"
}

resource "azurerm_role_assignment" "github_dev_contributor" {
  scope                = azurerm_resource_group.dev.id
  role_definition_name = "Contributor"
  principal_id         = azurerm_user_assigned_identity.github.principal_id
}

resource "azurerm_role_assignment" "github_state" {
  scope                = azurerm_storage_account.state.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = azurerm_user_assigned_identity.github.principal_id
}

# Owner does not include blob data access; whoever applies this layer also runs dev locally.
resource "azurerm_role_assignment" "deployer_state" {
  scope                = azurerm_storage_account.state.id
  role_definition_name = "Storage Blob Data Contributor"
  principal_id         = data.azurerm_client_config.current.object_id
}

# --- Budget ------------------------------------------------------------------------------------

resource "azurerm_consumption_budget_subscription" "this" {
  count = var.enable_budget ? 1 : 0

  name            = "budget-${var.project}"
  subscription_id = data.azurerm_subscription.current.id
  amount          = var.budget_amount
  time_grain      = "Monthly"

  time_period {
    start_date = var.budget_start_date
  }

  dynamic "notification" {
    for_each = [40, 60, 100]

    content {
      enabled        = true
      threshold      = notification.value
      threshold_type = "Actual"
      operator       = "GreaterThanOrEqualTo"
      contact_emails = var.budget_contact_emails
    }
  }

  lifecycle {
    precondition {
      condition     = length(var.budget_contact_emails) > 0
      error_message = "budget_contact_emails must list at least one address when the budget is enabled."
    }
  }
}
