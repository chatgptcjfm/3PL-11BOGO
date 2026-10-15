#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

if [[ ! -f .env ]]; then
  ./scripts/create-dev-env.sh
fi

sudo service mariadb start
./scripts/configure-dev-db.sh
mvn -B clean package

if command -v fuser >/dev/null 2>&1; then
  fuser -k 3000/tcp 2>/dev/null || true
fi
pm2 delete integration-process-bootstrap >/dev/null 2>&1 || true
pm2 start ecosystem.config.cjs

for attempt in $(seq 1 40); do
  if curl -fsS http://127.0.0.1:3000/api/health; then
    printf '\nStarted at http://127.0.0.1:3000\n'
    exit 0
  fi
  sleep 2
done

pm2 logs integration-process-bootstrap --nostream --lines 80 || true
echo 'Spring Boot did not become healthy within 80 seconds.' >&2
exit 1
