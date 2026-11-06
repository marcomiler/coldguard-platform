output "id" {
  description = "Environment resource ID."
  value       = azapi_resource.this.id
}

output "default_domain" {
  description = "Default domain of the environment."
  value       = azapi_resource.this.output.properties.defaultDomain
}
