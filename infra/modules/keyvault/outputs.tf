output "name" {
  description = "Key Vault name."
  value       = azurerm_key_vault.this.name
}

output "id" {
  description = "Key Vault resource ID."
  value       = azurerm_key_vault.this.id
}

output "secret_ids" {
  description = "Versionless secret IDs by name, used for Container Apps references (no secret values)."
  value       = { for name, secret in azurerm_key_vault_secret.this : name => secret.versionless_id }
}
