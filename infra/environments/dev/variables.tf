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

variable "ttl" {
  description = "Tag that tells when the environment must be destroyed, for example 2026-11-20."
  type        = string
}

variable "resource_group_name" {
  description = "Empty resource group created by the foundation layer."
  type        = string
  default     = "rg-coldguard-dev"
}

variable "image_prefix" {
  description = "Registry path in front of each service name, without a tag."
  type        = string
  default     = "ghcr.io/marcomiler/coldguard"
}

variable "image_tag" {
  description = "Immutable image tag to deploy, sha-<commit> or vX.Y.Z (never latest)."
  type        = string

  validation {
    condition     = can(regex("^(sha-[0-9a-f]{7,40}|v[0-9]+\\.[0-9]+\\.[0-9]+(-[0-9A-Za-z.]+)?)$", var.image_tag))
    error_message = "image_tag must be sha-<commit> or vX.Y.Z."
  }
}

variable "rabbitmq_image" {
  description = "RabbitMQ image (the plain one: the management UI is not needed)."
  type        = string
  default     = "rabbitmq:3.13"
}

variable "mailpit_image" {
  description = "Mailpit image."
  type        = string
  default     = "axllent/mailpit:v1.21"
}

variable "java_service_size" {
  description = "vCPU and memory of each Java service container."
  type = object({
    cpu    = number
    memory = string
  })
  default = {
    cpu    = 0.5
    memory = "1Gi"
  }
}

variable "deploy_mailpit" {
  description = "Deploy Mailpit so the notification e-mails can be shown."
  type        = bool
  default     = true
}

variable "deploy_frontend" {
  description = "Create the static site that serves the frontend. The Gateway then accepts its origin through CORS."
  type        = bool
  default     = true
}

variable "deploy_simulator" {
  description = "Deploy the sensor simulator. It needs a scenario file that this layer does not provide, so it is off by default."
  type        = bool
  default     = false
}

variable "cors_allowed_origins" {
  description = "Browser origins allowed to call the Gateway, comma-separated, explicit."
  type        = string
  default     = ""
}

variable "technical_endpoints_enabled" {
  description = "Enable the Gateway's technical entry points (platform administrator only)."
  type        = bool
  default     = false
}

variable "postgres_sku_name" {
  description = "PostgreSQL Flexible Server SKU (Burstable only)."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_extra_allowed_cidrs" {
  description = "Extra firewall ranges to inspect the database from a workstation, by rule name."
  type        = map(object({ start = string, end = string }))
  default     = {}
}
