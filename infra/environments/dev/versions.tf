terraform {
  required_version = ">= 1.9, < 2.0"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = ">= 4.30, < 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    tls = {
      source  = "hashicorp/tls"
      version = "~> 4.0"
    }
  }

  # Remote state in the foundation storage account. The values come from backend.hcl
  # (see backend.hcl.example), never from the code.
  backend "azurerm" {
    use_azuread_auth = true
  }
}

# The subscription comes from the environment (ARM_SUBSCRIPTION_ID), never from the code.
provider "azurerm" {
  features {
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = false
    }
  }
  # The resource providers are registered once by hand (see the README), not by Terraform.
  resource_provider_registrations = "none"
}
