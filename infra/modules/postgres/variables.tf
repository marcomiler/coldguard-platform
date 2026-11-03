variable "name" {
  description = "Server name (globally unique, lowercase)."
  type        = string
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the server."
  type        = string
}

variable "postgres_version" {
  description = "PostgreSQL major version."
  type        = string
  default     = "16"
}

variable "sku_name" {
  description = "Compute SKU. The default is the smallest Burstable size."
  type        = string
  default     = "B_Standard_B1ms"

  validation {
    condition     = startswith(var.sku_name, "B_")
    error_message = "Only Burstable SKUs (B_...) are allowed here to keep cost minimal."
  }
}

variable "storage_mb" {
  description = "Storage in MB. 32768 is the minimum."
  type        = number
  default     = 32768
}

variable "administrator_login" {
  description = "Administrator user name."
  type        = string
  default     = "coldguard"
}

variable "administrator_password" {
  description = "Administrator password."
  type        = string
  sensitive   = true
}

variable "database_name" {
  description = "Database shared by the services (one schema per service)."
  type        = string
  default     = "coldguard"
}

variable "extra_allowed_cidrs" {
  description = "Extra firewall ranges, by rule name, for example the operator's address to inspect the database."
  type        = map(object({ start = string, end = string }))
  default     = {}
}

variable "tags" {
  description = "Tags applied to the resources."
  type        = map(string)
  default     = {}
}
