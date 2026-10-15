#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$APP_DIR/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  DB_PASSWORD="$(openssl rand -hex 32)"
  umask 077
  cat > "$ENV_FILE" <<EOF
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=integration_process_dev
DB_USERNAME=integration_dev
DB_PASSWORD=$DB_PASSWORD
PORT=3000
EOF
fi
chmod 600 "$ENV_FILE"
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_NAME:?DB_NAME is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
[[ "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'DB_NAME must contain only letters, numbers, and underscores.' >&2; exit 1; }
[[ "$DB_USERNAME" =~ ^[A-Za-z0-9_]+$ ]] || { echo 'DB_USERNAME must contain only letters, numbers, and underscores.' >&2; exit 1; }
[[ "$DB_PASSWORD" =~ ^[A-Fa-f0-9]{32,}$ ]] || { echo 'DB_PASSWORD must be a 32+ character hexadecimal secret.' >&2; exit 1; }

sudo service mariadb start
sudo mariadb <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USERNAME'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'127.0.0.1';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USERNAME'@'localhost';
FLUSH PRIVILEGES;
SQL

printf 'Database and least-scope local account configured: %s / %s\n' "$DB_NAME" "$DB_USERNAME"
MYSQL_PWD="$DB_PASSWORD" mariadb --protocol=tcp -h "$DB_HOST" -P "$DB_PORT" \
  -u "$DB_USERNAME" "$DB_NAME" -N -e 'SELECT VERSION();'
