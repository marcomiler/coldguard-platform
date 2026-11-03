data "azurerm_client_config" "current" {}

locals {
  tags = {
    project = var.project
    env     = "dev"
    owner   = var.owner
    ttl     = var.ttl
  }

  prefix = "${var.project}-dev"

  # Internal gRPC services: ports must match the services' own configuration.
  grpc_services = {
    asset-service     = { grpc_port = 9091, management_port = 8081 }
    telemetry-service = { grpc_port = 9092, management_port = 8082 }
    incident-service  = { grpc_port = 9093, management_port = 8083 }
  }

  server_identities = toset(keys(local.grpc_services))
  client_identities = toset(["gateway", "sensor-simulator"])

  image = { for name in ["gateway", "asset-service", "telemetry-service", "incident-service", "notification-service", "sensor-simulator"] :
  name => "${var.image_prefix}/${name}:${var.image_tag}" }

  java_options = "-XX:MaxRAMPercentage=75 --enable-native-access=ALL-UNNAMED"

  rabbitmq_port = 5672
  smtp_port     = 1025
}

# --- Generated secrets -----------------------------------------------------------------------

resource "random_password" "database" {
  length  = 24
  special = false
}

resource "random_password" "rabbitmq" {
  length  = 24
  special = false
}

resource "random_password" "demo_users" {
  length  = 20
  special = false
}

module "pki" {
  source = "../../modules/pki"

  server_identities = local.server_identities
  client_identities = local.client_identities
}

# --- Platform --------------------------------------------------------------------------------

resource "azurerm_user_assigned_identity" "apps" {
  name                = "id-${local.prefix}-apps"
  resource_group_name = var.resource_group_name
  location            = var.location
  tags                = local.tags
}

module "observability" {
  source = "../../modules/observability"

  name                = "log-${local.prefix}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tags                = local.tags
}

module "keyvault" {
  source = "../../modules/keyvault"

  name_prefix          = "kv-cg-dev"
  location             = var.location
  resource_group_name  = var.resource_group_name
  reader_principal_ids = { apps = azurerm_user_assigned_identity.apps.principal_id }
  tags                 = local.tags

  secrets = merge(
    {
      "db-password"       = random_password.database.result
      "rabbitmq-password" = random_password.rabbitmq.result
      "demo-password"     = random_password.demo_users.result
      "ca-crt"            = module.pki.ca_certificate_pem
      "jwt-private-pem"   = module.pki.jwt_private_key_pem
      "jwt-public-pem"    = module.pki.jwt_public_key_pem
    },
    { for name in concat(tolist(local.server_identities), tolist(local.client_identities)) :
    "${name}-crt" => module.pki.leaf_certificate_pem[name] },
    { for name in concat(tolist(local.server_identities), tolist(local.client_identities)) :
    "${name}-key" => module.pki.leaf_private_key_pem[name] },
  )
}

module "postgres" {
  source = "../../modules/postgres"

  name                   = "psql-${local.prefix}-${substr(module.keyvault.id, -4, 4)}"
  location               = var.location
  resource_group_name    = var.resource_group_name
  sku_name               = var.postgres_sku_name
  administrator_password = random_password.database.result
  extra_allowed_cidrs    = var.postgres_extra_allowed_cidrs
  tags                   = local.tags
}

module "environment" {
  source = "../../modules/containerapps-env"

  name                       = "cae-${local.prefix}"
  location                   = var.location
  resource_group_name        = var.resource_group_name
  log_analytics_workspace_id = module.observability.workspace_id
  tags                       = local.tags
}

# --- Applications ----------------------------------------------------------------------------
# Key Vault secret IDs by the name the app sees. Dots are allowed in app secret names, and the
# services expect files such as gateway.crt, so the file name is the secret name.

locals {
  kv = module.keyvault.secret_ids

  mtls_secrets = {
    for name in concat(tolist(local.server_identities), tolist(local.client_identities)) : name => {
      "${name}.crt" = local.kv["${name}-crt"]
      "${name}.key" = local.kv["${name}-key"]
      "ca.crt"      = local.kv["ca-crt"]
    }
  }

  database_env = {
    DB_HOST = module.postgres.fqdn
    DB_PORT = "5432"
    DB_NAME = module.postgres.database_name
    DB_USER = module.postgres.administrator_login
  }

  amqp_env = {
    RABBITMQ_HOST = "rabbitmq"
    RABBITMQ_PORT = tostring(local.rabbitmq_port)
    RABBITMQ_USER = "coldguard"
  }

  common_env = {
    JAVA_TOOL_OPTIONS      = local.java_options
    TRACING_EXPORT_ENABLED = "false"
  }
}

module "rabbitmq" {
  source = "../../modules/container-app"

  name                = "rabbitmq"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = var.rabbitmq_image
  cpu                 = 0.25
  memory              = "0.5Gi"
  tags                = local.tags

  env = {
    RABBITMQ_DEFAULT_USER = "coldguard"
  }
  secrets    = { "rabbitmq-password" = local.kv["rabbitmq-password"] }
  secret_env = { RABBITMQ_DEFAULT_PASS = "rabbitmq-password" }

  ingress = { external = false, target_port = local.rabbitmq_port, transport = "tcp" }
  probe   = { port = local.rabbitmq_port, initial_delay_seconds = 30 }

  depends_on = [module.keyvault]
}

module "mailpit" {
  source = "../../modules/container-app"
  count  = var.deploy_mailpit ? 1 : 0

  name                = "mailpit"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = var.mailpit_image
  cpu                 = 0.25
  memory              = "0.5Gi"
  tags                = local.tags

  ingress = { external = false, target_port = local.smtp_port, transport = "tcp" }
  probe   = { port = local.smtp_port }
}

module "asset_service" {
  source = "../../modules/container-app"

  name                = "asset-service"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["asset-service"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  env = merge(local.common_env, local.database_env, local.amqp_env, {
    GRPC_SERVER_PORT                   = tostring(local.grpc_services["asset-service"].grpc_port)
    TLS_CERTS_DIR                      = "/certs"
    ASSET_CALIBRATION_DEFAULT_VALIDITY = "P90D"
  })
  secrets = merge(local.mtls_secrets["asset-service"], {
    "db-password"       = local.kv["db-password"]
    "rabbitmq-password" = local.kv["rabbitmq-password"]
  })
  secret_env = {
    DB_PASSWORD       = "db-password"
    RABBITMQ_PASSWORD = "rabbitmq-password"
  }
  secret_mount_paths = ["/certs/asset-service", "/certs/ca"]

  ingress = { external = false, target_port = local.grpc_services["asset-service"].grpc_port, transport = "tcp" }
  probe   = { port = local.grpc_services["asset-service"].management_port, path = "/actuator/health/readiness" }

  depends_on = [module.postgres, module.rabbitmq]
}

module "telemetry_service" {
  source = "../../modules/container-app"

  name                = "telemetry-service"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["telemetry-service"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  env = merge(local.common_env, local.database_env, local.amqp_env, {
    GRPC_SERVER_PORT        = tostring(local.grpc_services["telemetry-service"].grpc_port)
    TLS_CERTS_DIR           = "/certs"
    ASSET_SERVICE_HOST      = "asset-service"
    ASSET_SERVICE_GRPC_PORT = tostring(local.grpc_services["asset-service"].grpc_port)
  })
  secrets = merge(local.mtls_secrets["telemetry-service"], {
    "db-password"       = local.kv["db-password"]
    "rabbitmq-password" = local.kv["rabbitmq-password"]
  })
  secret_env = {
    DB_PASSWORD       = "db-password"
    RABBITMQ_PASSWORD = "rabbitmq-password"
  }
  secret_mount_paths = ["/certs/telemetry-service", "/certs/ca"]

  ingress = { external = false, target_port = local.grpc_services["telemetry-service"].grpc_port, transport = "tcp" }
  probe   = { port = local.grpc_services["telemetry-service"].management_port, path = "/actuator/health/readiness" }

  depends_on = [module.asset_service]
}

module "incident_service" {
  source = "../../modules/container-app"

  name                = "incident-service"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["incident-service"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  env = merge(local.common_env, local.database_env, local.amqp_env, {
    GRPC_SERVER_PORT           = tostring(local.grpc_services["incident-service"].grpc_port)
    TLS_CERTS_DIR              = "/certs"
    IDENTITY_BOOTSTRAP_ENABLED = "true"
  })
  secrets = merge(local.mtls_secrets["incident-service"], {
    "db-password"       = local.kv["db-password"]
    "rabbitmq-password" = local.kv["rabbitmq-password"]
    "demo-password"     = local.kv["demo-password"]
  })
  secret_env = {
    DB_PASSWORD         = "db-password"
    RABBITMQ_PASSWORD   = "rabbitmq-password"
    DEMO_USERS_PASSWORD = "demo-password"
  }
  secret_mount_paths = ["/certs/incident-service", "/certs/ca"]

  ingress = { external = false, target_port = local.grpc_services["incident-service"].grpc_port, transport = "tcp" }
  probe   = { port = local.grpc_services["incident-service"].management_port, path = "/actuator/health/readiness" }

  depends_on = [module.postgres, module.rabbitmq]
}

module "notification_service" {
  source = "../../modules/container-app"

  name                = "notification-service"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["notification-service"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  env = merge(local.common_env, local.database_env, local.amqp_env, {
    SMTP_HOST = "mailpit"
    SMTP_PORT = tostring(local.smtp_port)
  })
  secrets = {
    "db-password"       = local.kv["db-password"]
    "rabbitmq-password" = local.kv["rabbitmq-password"]
  }
  secret_env = {
    DB_PASSWORD       = "db-password"
    RABBITMQ_PASSWORD = "rabbitmq-password"
  }

  probe = { port = 8084, path = "/actuator/health/readiness" }

  depends_on = [module.postgres, module.rabbitmq, module.mailpit]
}

module "gateway" {
  source = "../../modules/container-app"

  name                = "gateway"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["gateway"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  env = merge(local.common_env, {
    SERVER_PORT                           = "8080"
    MANAGEMENT_PORT                       = "8090"
    TLS_CERTS_DIR                         = "/certs"
    JWT_PRIVATE_KEY_PATH                  = "/jwt/jwt-private.pem"
    JWT_PUBLIC_KEY_PATH                   = "/jwt/jwt-public.pem"
    INCIDENT_SERVICE_HOST                 = "incident-service"
    INCIDENT_SERVICE_GRPC_PORT            = tostring(local.grpc_services["incident-service"].grpc_port)
    ASSET_SERVICE_HOST                    = "asset-service"
    ASSET_SERVICE_GRPC_PORT               = tostring(local.grpc_services["asset-service"].grpc_port)
    TELEMETRY_SERVICE_HOST                = "telemetry-service"
    TELEMETRY_SERVICE_GRPC_PORT           = tostring(local.grpc_services["telemetry-service"].grpc_port)
    COLDGUARD_CORS_ALLOWED_ORIGINS        = var.cors_allowed_origins
    COLDGUARD_TECHNICAL_ENDPOINTS_ENABLED = tostring(var.technical_endpoints_enabled)
  })
  secrets = merge(local.mtls_secrets["gateway"], {
    "jwt-private.pem" = local.kv["jwt-private-pem"]
    "jwt-public.pem"  = local.kv["jwt-public-pem"]
  })
  secret_mount_paths = ["/certs/gateway", "/certs/ca", "/jwt"]

  ingress = { external = true, target_port = 8080, transport = "http" }
  probe   = { port = 8090, path = "/actuator/health/readiness" }

  depends_on = [module.asset_service, module.telemetry_service, module.incident_service]
}

module "sensor_simulator" {
  source = "../../modules/container-app"
  count  = var.deploy_simulator ? 1 : 0

  name                = "sensor-simulator"
  resource_group_name = var.resource_group_name
  environment_id      = module.environment.id
  identity_id         = azurerm_user_assigned_identity.apps.id
  image               = local.image["sensor-simulator"]
  cpu                 = var.java_service_size.cpu
  memory              = var.java_service_size.memory
  tags                = local.tags

  # Starts idle: the scenario needs the sensor IDs created by the seed, so the demo workflow injects
  # it later (and a new revision restarts the scenario clock).
  env = merge(local.common_env, {
    SIMULATOR_ENABLED           = "false"
    SERVER_PORT                 = "8085"
    TLS_CERTS_DIR               = "/certs"
    TELEMETRY_SERVICE_HOST      = "telemetry-service"
    TELEMETRY_SERVICE_GRPC_PORT = tostring(local.grpc_services["telemetry-service"].grpc_port)
  })
  secrets            = local.mtls_secrets["sensor-simulator"]
  secret_mount_paths = ["/certs/sensor-simulator", "/certs/ca"]

  probe = { port = 8085, path = "/actuator/health/readiness" }

  depends_on = [module.telemetry_service]
}
