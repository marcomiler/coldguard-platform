output "name" {
  description = "Storage account name."
  value       = azurerm_storage_account.this.name
}

output "url" {
  description = "Public HTTPS URL of the site, without a trailing slash."
  value       = trimsuffix(azurerm_storage_account.this.primary_web_endpoint, "/")
}
