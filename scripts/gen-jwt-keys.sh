#!/usr/bin/env bash
# Generates a throwaway RSA keypair for the authenticate-service / gateway JWT
# config and prints it as JWT_PRIVATE_KEY / JWT_PUBLIC_KEY env-file lines.
#
# Usage: scripts/gen-jwt-keys.sh > .env
#
# authenticate-service's RsaKeyProvider expects base64-encoded DER (PKCS8 for
# the private key, X509/SubjectPublicKeyInfo for the public key) — it strips
# PEM headers/whitespace before decoding, so raw base64 is the simplest form.
set -euo pipefail

tmpdir=$(mktemp -d)
trap 'rm -rf "$tmpdir"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$tmpdir/private.pem" 2>/dev/null

private_b64=$(openssl pkcs8 -topk8 -nocrypt -in "$tmpdir/private.pem" -outform DER | base64 -w0)
public_b64=$(openssl rsa -pubout -in "$tmpdir/private.pem" -outform DER 2>/dev/null | base64 -w0)

echo "JWT_PRIVATE_KEY=${private_b64}"
echo "JWT_PUBLIC_KEY=${public_b64}"
