variable "name" {
  description = "Name of the Log Analytics workspace."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the workspace."
  type        = string
}

variable "retention_in_days" {
  description = "Log retention. 30 is the minimum of the PerGB2018 SKU."
  type        = number
  default     = 30

  validation {
    condition     = var.retention_in_days >= 30 && var.retention_in_days <= 730
    error_message = "retention_in_days must be between 30 and 730."
  }
}

variable "daily_quota_gb" {
  description = "Daily ingestion cap in GB, to bound cost."
  type        = number
  default     = 0.5

  validation {
    condition     = var.daily_quota_gb >= 0.023
    error_message = "daily_quota_gb must be at least 0.023."
  }
}

variable "tags" {
  description = "Tags applied to the resource."
  type        = map(string)
  default     = {}
}
