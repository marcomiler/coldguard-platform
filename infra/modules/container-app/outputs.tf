output "name" {
  description = "Container app name."
  value       = azurerm_container_app.this.name
}

output "fqdn" {
  description = "Ingress FQDN, or null when the app has no ingress."
  value       = try(azurerm_container_app.this.ingress[0].fqdn, null)
}
