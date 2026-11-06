output "workspace_id" {
  description = "Resource ID of the Log Analytics workspace."
  value       = azurerm_log_analytics_workspace.this.id
}

output "workspace_customer_id" {
  description = "Workspace (customer) ID that log shippers use to identify the workspace."
  value       = azurerm_log_analytics_workspace.this.workspace_id
}

output "primary_shared_key" {
  description = "Primary shared key of the workspace, used by the Container Apps environment to ship logs."
  value       = azurerm_log_analytics_workspace.this.primary_shared_key
  sensitive   = true
}
