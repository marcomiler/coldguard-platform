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

variable "log_analytics_workspace_id" {
  description = "Workspace that receives the container logs."
  type        = string
}

variable "tags" {
  description = "Tags applied to the resource."
  type        = map(string)
  default     = {}
}
