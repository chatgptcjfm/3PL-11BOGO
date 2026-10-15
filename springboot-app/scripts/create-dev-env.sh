#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

if [[ -e .env ]]; then
  echo '.env already exists; leaving it unchanged.'
  exit 0
fi

if ! command -v openssl >/dev/null 2>&1; then
  echo 'openssl is required to generate a local database password.' >&2
  exit 1
fi

umask 077
password="$(openssl rand -hex 24)"
cat > .env <<EOF
DB_NAME=integration_process_dev
DB_USERNAME=integration_dev
DB_PASSWORD=${password}
DB_URL=jdbc:mariadb://127.0.0.1:3306/integration_process_dev?useUnicode=true&characterEncoding=utf8
PORT=3000
EOF
chmod 600 .env
unset password
echo 'Created private .env with a random local-only database password.'
