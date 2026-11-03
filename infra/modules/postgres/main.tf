terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

resource "azurerm_postgresql_flexible_server" "this" {
  name                = var.name
  location            = var.location
  resource_group_name = var.resource_group_name

  version                = var.postgres_version
  sku_name               = var.sku_name
  storage_mb             = var.storage_mb
  administrator_login    = var.administrator_login
  administrator_password = var.administrator_password

  # Cheapest shape: one zone, no high availability, no private network (see the decision record).
  public_network_access_enabled = true
  backup_retention_days         = 7
  geo_redundant_backup_enabled  = false

  tags = var.tags

  lifecycle {
    # Azure picks the zone; pinning it would fail where that zone has no capacity.
    ignore_changes = [zone]
  }
}

resource "azurerm_postgresql_flexible_server_database" "this" {
  name      = var.database_name
  server_id = azurerm_postgresql_flexible_server.this.id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

# 0.0.0.0 - 0.0.0.0 is Azure's convention for "allow Azure services"; TLS stays mandatory.
resource "azurerm_postgresql_flexible_server_firewall_rule" "azure_services" {
  name             = "allow-azure-services"
  server_id        = azurerm_postgresql_flexible_server.this.id
  start_ip_address = "0.0.0.0"
  end_ip_address   = "0.0.0.0"
}

resource "azurerm_postgresql_flexible_server_firewall_rule" "extra" {
  for_each = var.extra_allowed_cidrs

  name             = each.key
  server_id        = azurerm_postgresql_flexible_server.this.id
  start_ip_address = each.value.start
  end_ip_address   = each.value.end
}
