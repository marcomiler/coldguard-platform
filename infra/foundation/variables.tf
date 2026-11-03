variable "location" {
  description = "Azure region. Must be allowed by the subscription policy."
  type        = string
  default     = "canadacentral"
}

variable "project" {
  description = "Project name used in resource names and tags."
  type        = string
  default     = "coldguard"
}

variable "owner" {
  description = "Owner tag value (a team or person alias, not an e-mail)."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to authenticate through OIDC, as owner/name."
  type        = string
  default     = "marcomiler/coldguard-platform"

  validation {
    condition     = can(regex("^[A-Za-z0-9._-]+/[A-Za-z0-9._-]+$", var.github_repository))
    error_message = "github_repository must look like owner/name."
  }
}

variable "github_environment" {
  description = "Protected GitHub environment whose approval gates every apply and destroy."
  type        = string
  default     = "azure-dev"
}

variable "enable_budget" {
  description = "Create the subscription budget with alerts. Disable if the subscription offer does not support budgets."
  type        = bool
  default     = true
}

variable "budget_amount" {
  description = "Hard ceiling of the budget in USD; alerts fire at 40, 60 and 100 percent (10, 15 and 25 USD by default)."
  type        = number
  default     = 25
}

variable "budget_start_date" {
  description = "First day of a month, in RFC 3339, for example 2026-11-01T00:00:00Z."
  type        = string
  default     = "2026-11-01T00:00:00Z"

  validation {
    condition     = can(regex("^[0-9]{4}-[0-9]{2}-01T00:00:00Z$", var.budget_start_date))
    error_message = "budget_start_date must be the first day of a month at 00:00:00Z."
  }
}

variable "budget_contact_emails" {
  description = "Addresses that receive the budget alerts."
  type        = list(string)
  default     = []
}
