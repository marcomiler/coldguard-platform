#!/usr/bin/env bash
# Generates a local, development-only CA and one leaf certificate per internal identity
# (servers: asset-service, telemetry-service, incident-service; clients: gateway,
# sensor-simulator) used for mTLS on every internal gRPC channel in the local Docker Compose
# stack. Never run this against a real environment:
# these certificates are self-signed, long-lived, and their private keys are written to
# disk in plaintext under deploy/local/certs/, which is git-ignored on purpose.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CERTS_DIR="$REPO_ROOT/deploy/local/certs"
CA_DIR="$CERTS_DIR/ca"

# Identities that also act as gRPC servers get hostname SANs; client-only identities get none.
SERVER_IDENTITIES=(asset-service telemetry-service incident-service)
CLIENT_IDENTITIES=(gateway sensor-simulator)

VALIDITY_DAYS=825
FORCE=false

usage() {
  echo "Usage: $0 [--force]"
  echo "  --force  Overwrite existing certificates. Without it, the script refuses to"
  echo "           run if deploy/local/certs already contains a CA or leaf certificate."
}

for arg in "$@"; do
  case "$arg" in
    --force) FORCE=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $arg" >&2; usage >&2; exit 1 ;;
  esac
done

if ! command -v openssl >/dev/null 2>&1; then
  echo "ERROR: openssl is required to generate development certificates but was not found in PATH." >&2
  exit 1
fi

if [[ "$FORCE" != "true" ]]; then
  for existing in "$CA_DIR/ca.crt" \
    "$CERTS_DIR"/*/*.crt; do
    if [[ -f "$existing" ]]; then
      echo "ERROR: $existing already exists. Re-run with --force to regenerate all certificates." >&2
      echo "Regenerating invalidates every certificate previously trusted by the CA: restart" >&2
      echo "any running Gateway/Incident Service containers afterwards." >&2
      exit 1
    fi
  done
fi

mkdir -p "$CA_DIR"

echo "Generating local development CA (coldguard-local-ca, ${VALIDITY_DAYS} days)..."
openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout "$CA_DIR/ca.key" -out "$CA_DIR/ca.crt" \
  -days "$VALIDITY_DAYS" -subj "/CN=coldguard-local-ca"

issue_leaf_certificate() {
  local name="$1" out_dir="$2" san_extension="$3"
  local key="$out_dir/$name.key" csr="$out_dir/$name.csr" crt="$out_dir/$name.crt"

  openssl req -newkey rsa:2048 -nodes -keyout "$key" -out "$csr" -subj "/CN=$name"

  local extfile
  extfile="$(mktemp)"
  trap 'rm -f "$extfile"' RETURN
  {
    echo "basicConstraints=CA:FALSE"
    echo "keyUsage=digitalSignature,keyEncipherment"
    echo "extendedKeyUsage=serverAuth,clientAuth"
    if [[ -n "$san_extension" ]]; then
      echo "subjectAltName=$san_extension"
    fi
  } > "$extfile"

  openssl x509 -req -in "$csr" \
    -CA "$CA_DIR/ca.crt" -CAkey "$CA_DIR/ca.key" -CAcreateserial \
    -out "$crt" -days "$VALIDITY_DAYS" -extfile "$extfile"

  rm -f "$csr"
}

for name in "${SERVER_IDENTITIES[@]}"; do
  mkdir -p "$CERTS_DIR/$name"
  echo "Issuing $name certificate (SANs: $name, localhost, host.docker.internal, 127.0.0.1)..."
  issue_leaf_certificate "$name" "$CERTS_DIR/$name" \
    "DNS:$name,DNS:localhost,DNS:host.docker.internal,IP:127.0.0.1"
done

for name in "${CLIENT_IDENTITIES[@]}"; do
  mkdir -p "$CERTS_DIR/$name"
  echo "Issuing $name client certificate (no hostname is ever verified against it)..."
  issue_leaf_certificate "$name" "$CERTS_DIR/$name" ""
done

# The CA private key never leaves the host and is never mounted for reading by a container:
# owner-only access.
chmod 600 "$CA_DIR/ca.key"
# The leaf private keys are read by their respective containers through a read-only
# bind mount; group-read (not world-read) lets the container's non-root user read them
# without exposing them to other accounts on the host. This relies on the key files already
# belonging to the current user's primary group (the default for newly created files), which
# matches the fixed GID assigned to the container's service user (see the Dockerfiles).
for name in "${SERVER_IDENTITIES[@]}" "${CLIENT_IDENTITIES[@]}"; do
  chmod 640 "$CERTS_DIR/$name/$name.key"
done

echo ""
echo "Done. Generated under $CERTS_DIR (git-ignored, development-only, never commit):"
echo "  $CA_DIR/ca.crt / ca.key"
for name in "${SERVER_IDENTITIES[@]}" "${CLIENT_IDENTITIES[@]}"; do
  echo "  $CERTS_DIR/$name/$name.crt / $name.key"
done
