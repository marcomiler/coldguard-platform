output "id" {
  description = "Environment resource ID."
  value       = azurerm_container_app_environment.this.id
}

output "default_domain" {
  description = "Default domain of the environment."
  value       = azurerm_container_app_environment.this.default_domain
}
