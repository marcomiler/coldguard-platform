terraform {
  required_providers {
    azapi = {
      source = "Azure/azapi"
    }
    azurerm = {
      source = "hashicorp/azurerm"
    }
  }
}

data "azurerm_resource_group" "this" {
  name = var.resource_group_name
}

# Created with azapi because azurerm cannot choose the environment mode, and the platform now makes
# an "express" environment when none is given (no TCP ingress, no Key Vault secret references).
# WorkloadProfiles with only the Consumption profile is billed per second of use, with no dedicated
# nodes.
resource "azapi_resource" "this" {
  type      = "Microsoft.App/managedEnvironments@2026-07-01"
  name      = var.name
  location  = var.location
  parent_id = data.azurerm_resource_group.this.id
  tags      = var.tags

  # The provider's embedded schema stops at 2026-01-01 and does not know environmentMode, which the
  # service accepts from 2026-07-01 on, so the local check is off for this one resource.
  schema_validation_enabled = false

  body = {
    properties = {
      environmentMode = "WorkloadProfiles"
      workloadProfiles = [{
        name                = "Consumption"
        workloadProfileType = "Consumption"
      }]
      appLogsConfiguration = {
        destination = "log-analytics"
        logAnalyticsConfiguration = {
          customerId = var.log_analytics_customer_id
        }
      }
    }
  }

  sensitive_body = {
    properties = {
      appLogsConfiguration = {
        logAnalyticsConfiguration = {
          sharedKey = var.log_analytics_shared_key
        }
      }
    }
  }

  response_export_values = ["properties.defaultDomain"]
}
