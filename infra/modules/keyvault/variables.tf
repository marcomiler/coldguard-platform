variable "name_prefix" {
  description = "Prefix of the vault name; a random suffix is appended (3 to 20 characters in total)."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,14}$", var.name_prefix))
    error_message = "name_prefix must start with a letter and use 2 to 15 lowercase letters, digits or dashes."
  }
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the vault."
  type        = string
}

variable "reader_principal_ids" {
  description = "Object IDs (keyed by a static label) that may read secrets."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "Secrets to store, by name (letters, digits and dashes only)."
  type        = map(string)
  sensitive   = true
  default     = {}
}

variable "tags" {
  description = "Tags applied to the resources."
  type        = map(string)
  default     = {}
}
