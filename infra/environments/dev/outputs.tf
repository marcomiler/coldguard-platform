output "gateway_url" {
  description = "Public HTTPS URL of the Gateway."
  value       = "https://${module.gateway.fqdn}"
}

output "key_vault_name" {
  description = "Key Vault that holds the generated secrets (for example demo-password)."
  value       = module.keyvault.name
}

output "postgres_fqdn" {
  description = "PostgreSQL server host name."
  value       = module.postgres.fqdn
}

output "container_apps_domain" {
  description = "Default domain of the Container Apps environment."
  value       = module.environment.default_domain
}

output "demo_users_password" {
  description = "Password of the demo users, read by the post-deployment smoke test."
  value       = random_password.demo_users.result
  sensitive   = true
}
