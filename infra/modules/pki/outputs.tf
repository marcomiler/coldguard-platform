output "ca_certificate_pem" {
  description = "CA certificate every service trusts."
  value       = tls_self_signed_cert.ca.cert_pem
}

output "leaf_certificate_pem" {
  description = "Leaf certificate by identity."
  value       = { for name, cert in tls_locally_signed_cert.leaf : name => cert.cert_pem }
}

output "leaf_private_key_pem" {
  description = "Leaf private key (PKCS#8) by identity."
  value       = { for name, key in tls_private_key.leaf : name => key.private_key_pem_pkcs8 }
  sensitive   = true
}

output "jwt_private_key_pem" {
  description = "Gateway signing key (PKCS#8)."
  value       = tls_private_key.jwt.private_key_pem_pkcs8
  sensitive   = true
}

output "jwt_public_key_pem" {
  description = "Gateway verification key (X.509 SubjectPublicKeyInfo)."
  value       = tls_private_key.jwt.public_key_pem
}
