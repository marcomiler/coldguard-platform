variable "name" {
  description = "Container app name. For internal gRPC services it is also the host name other apps use, so it must match the name in the service certificate."
  type        = string
}

variable "resource_group_name" {
  description = "Resource group that holds the app."
  type        = string
}

variable "environment_id" {
  description = "Container Apps environment ID."
  type        = string
}

variable "identity_id" {
  description = "User-assigned identity that reads the Key Vault secrets."
  type        = string
}

variable "image" {
  description = "Full image reference, including an immutable tag."
  type        = string

  validation {
    condition     = !endswith(var.image, ":latest") && can(regex(":", var.image))
    error_message = "The image must carry an explicit tag other than latest."
  }
}

variable "cpu" {
  description = "vCPU of the container."
  type        = number
}

variable "memory" {
  description = "Memory of the container, for example 1Gi."
  type        = string
}

variable "min_replicas" {
  description = "Minimum replicas."
  type        = number
  default     = 1
}

variable "max_replicas" {
  description = "Maximum replicas."
  type        = number
  default     = 1
}

variable "env" {
  description = "Plain environment variables."
  type        = map(string)
  default     = {}
}

variable "secrets" {
  description = "App secret name to versionless Key Vault secret ID. The name is also the file name when the secret volume is mounted."
  type        = map(string)
  default     = {}
}

variable "secret_env" {
  description = "Environment variable name to app secret name."
  type        = map(string)
  default     = {}
}

variable "secret_mount_paths" {
  description = "Folders where the app secrets are mounted as files."
  type        = list(string)
  default     = []
}

variable "ingress" {
  description = "Ingress settings, or null for no ingress."
  type = object({
    external    = bool
    target_port = number
    transport   = string
  })
  default = null

  validation {
    condition     = var.ingress == null || contains(["http", "tcp"], try(var.ingress.transport, ""))
    error_message = "ingress.transport must be http or tcp."
  }
}

variable "probe" {
  description = "Readiness and startup probe. A null path means a TCP probe."
  type = object({
    port                  = number
    path                  = optional(string)
    initial_delay_seconds = optional(number, 20)
  })
  default = null
}

variable "tags" {
  description = "Tags applied to the resource."
  type        = map(string)
  default     = {}
}
