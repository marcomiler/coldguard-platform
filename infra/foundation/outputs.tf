output "tenant_id" {
  description = "Tenant ID, a repository variable for the workflows' azure/login."
  value       = data.azurerm_subscription.current.tenant_id
}

output "subscription_id" {
  description = "Subscription ID, a repository variable for the workflows' azure/login."
  value       = data.azurerm_subscription.current.subscription_id
}

output "github_identity_client_id" {
  description = "Client ID of the identity GitHub Actions uses."
  value       = azurerm_user_assigned_identity.github.client_id
}

output "state_resource_group_name" {
  description = "Resource group of the state storage account."
  value       = azurerm_resource_group.foundation.name
}

output "state_storage_account_name" {
  description = "Storage account of the remote state."
  value       = azurerm_storage_account.state.name
}

output "state_container_name" {
  description = "Container of the remote state."
  value       = azurerm_storage_container.state.name
}

output "dev_resource_group_name" {
  description = "Empty resource group the dev layer fills."
  value       = azurerm_resource_group.dev.name
}
