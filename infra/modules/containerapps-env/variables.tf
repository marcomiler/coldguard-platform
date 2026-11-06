variable "name" {
  description = "Environment name."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the environment."
  type        = string
}

variable "log_analytics_customer_id" {
  description = "Customer (workspace) ID of the Log Analytics workspace that receives the container logs."
  type        = string
}

variable "log_analytics_shared_key" {
  description = "Primary shared key of that workspace."
  type        = string
  sensitive   = true
}

variable "tags" {
  description = "Tags applied to the resource."
  type        = map(string)
  default     = {}
}
