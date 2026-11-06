terraform {
  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

# Workload-profiles environment with only the Consumption profile: billed per second of use, no
# dedicated nodes. The profile is declared explicitly because an environment created without one is
# an "express" environment, which rejects TCP ingress and Key Vault secret references.
resource "azurerm_container_app_environment" "this" {
  name                       = var.name
  location                   = var.location
  resource_group_name        = var.resource_group_name
  log_analytics_workspace_id = var.log_analytics_workspace_id
  tags                       = var.tags

  workload_profile {
    name                  = "Consumption"
    workload_profile_type = "Consumption"
  }
}
