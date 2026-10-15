#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

if [[ ! -f .env ]]; then
  echo 'Missing .env. Run ./scripts/create-dev-env.sh first.' >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

if [[ ! "$DB_NAME" =~ ^[a-zA-Z0-9_]+$ || ! "$DB_USERNAME" =~ ^[a-zA-Z0-9_]+$ ]]; then
  echo 'DB_NAME and DB_USERNAME may contain only letters, digits and underscores.' >&2
  exit 1
fi
if [[ ! "$DB_PASSWORD" =~ ^[a-fA-F0-9]+$ ]]; then
  echo 'DB_PASSWORD must be hexadecimal (the generated local password meets this requirement).' >&2
  exit 1
fi

sudo mariadb --protocol=socket <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'127.0.0.1';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "Configured local database '$DB_NAME' and restricted app user '$DB_USERNAME'."
