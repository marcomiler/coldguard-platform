terraform {
  required_providers {
    tls = {
      source = "hashicorp/tls"
    }
  }
}

locals {
  identities = merge(
    { for name in var.server_identities : name => [name] },
    { for name in var.client_identities : name => [] },
  )
}

# Demonstration CA: regenerated on every environment, never a production CA.
resource "tls_private_key" "ca" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_self_signed_cert" "ca" {
  private_key_pem       = tls_private_key.ca.private_key_pem
  is_ca_certificate     = true
  validity_period_hours = var.validity_hours
  allowed_uses          = ["cert_signing", "crl_signing", "digital_signature"]

  subject {
    common_name = var.ca_common_name
  }
}

resource "tls_private_key" "leaf" {
  for_each = local.identities

  algorithm = "RSA"
  rsa_bits  = 2048
}

resource "tls_cert_request" "leaf" {
  for_each = local.identities

  private_key_pem = tls_private_key.leaf[each.key].private_key_pem
  # Server identities get their own name as SAN, the name other apps use to reach them. Clients
  # get none: no host name is ever checked against them.
  dns_names = each.value

  subject {
    common_name = each.key
  }
}

resource "tls_locally_signed_cert" "leaf" {
  for_each = local.identities

  cert_request_pem      = tls_cert_request.leaf[each.key].cert_request_pem
  ca_private_key_pem    = tls_private_key.ca.private_key_pem
  ca_cert_pem           = tls_self_signed_cert.ca.cert_pem
  validity_period_hours = var.validity_hours
  allowed_uses          = ["digital_signature", "key_encipherment", "server_auth", "client_auth"]
}

# Gateway signing key pair for the access tokens (RS256).
resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 2048
}
