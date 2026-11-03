output "fqdn" {
  description = "Server host name."
  value       = azurerm_postgresql_flexible_server.this.fqdn
}

output "database_name" {
  description = "Database name."
  value       = azurerm_postgresql_flexible_server_database.this.name
}

output "administrator_login" {
  description = "Administrator user name."
  value       = azurerm_postgresql_flexible_server.this.administrator_login
}
