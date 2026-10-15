#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

./scripts/setup-sandbox.sh
./scripts/create-dev-env.sh
./scripts/configure-dev-db.sh
mvn -B clean verify
