terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}

data "azurerm_client_config" "current" {}

# Key Vault names are global; the suffix also avoids clashes with a soft-deleted vault.
resource "random_string" "suffix" {
  length  = 4
  upper   = false
  special = false
}

resource "azurerm_key_vault" "this" {
  name                = "${var.name_prefix}-${random_string.suffix.result}"
  location            = var.location
  resource_group_name = var.resource_group_name
  tenant_id           = data.azurerm_client_config.current.tenant_id
  sku_name            = "standard"

  # Access policies (not RBAC) so that no role assignment is required to read secrets.
  rbac_authorization_enabled = false
  soft_delete_retention_days = 7
  purge_protection_enabled   = false

  tags = var.tags
}

# Whoever runs Terraform writes the secrets.
resource "azurerm_key_vault_access_policy" "deployer" {
  key_vault_id       = azurerm_key_vault.this.id
  tenant_id          = data.azurerm_client_config.current.tenant_id
  object_id          = data.azurerm_client_config.current.object_id
  secret_permissions = ["Get", "List", "Set", "Delete", "Purge", "Recover"]
}

# The applications only read.
resource "azurerm_key_vault_access_policy" "readers" {
  for_each = var.reader_principal_ids

  key_vault_id       = azurerm_key_vault.this.id
  tenant_id          = data.azurerm_client_config.current.tenant_id
  object_id          = each.value
  secret_permissions = ["Get", "List"]
}

resource "azurerm_key_vault_secret" "this" {
  # Only the names decide the number of secrets; the values stay sensitive.
  for_each = nonsensitive(toset(keys(var.secrets)))

  name         = each.key
  value        = var.secrets[each.key]
  key_vault_id = azurerm_key_vault.this.id
  content_type = "text/plain"
  tags         = var.tags

  depends_on = [azurerm_key_vault_access_policy.deployer]
}
