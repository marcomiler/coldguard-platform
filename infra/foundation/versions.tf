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
  }

  # Local state on purpose: this layer creates the storage account that holds the remote state of
  # the others. The state file is git-ignored; it holds no generated secrets.
}

# The subscription comes from the environment (ARM_SUBSCRIPTION_ID), never from the code.
provider "azurerm" {
  features {}
}
