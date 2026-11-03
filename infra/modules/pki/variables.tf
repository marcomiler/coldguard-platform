variable "server_identities" {
  description = "Identities that act as gRPC servers; each gets itself as DNS SAN."
  type        = set(string)
}

variable "client_identities" {
  description = "Identities that only act as gRPC clients; no SAN."
  type        = set(string)
}

variable "ca_common_name" {
  description = "Common name of the demonstration CA."
  type        = string
  default     = "coldguard-azure-demo-ca"
}

variable "validity_hours" {
  description = "Validity of the CA and the leaf certificates."
  type        = number
  default     = 2160 # 90 days
}
