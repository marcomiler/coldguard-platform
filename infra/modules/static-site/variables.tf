variable "name" {
  description = "Storage account name: 3 to 24 lower case letters and digits, unique in Azure."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]{3,24}$", var.name))
    error_message = "name must be 3 to 24 lower case letters and digits."
  }
}

variable "location" {
  description = "Azure region."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the account."
  type        = string
}

variable "tags" {
  description = "Tags applied to the resource."
  type        = map(string)
  default     = {}
}
