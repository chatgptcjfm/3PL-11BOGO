#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

./scripts/bootstrap-sandbox.sh
./scripts/setup-local-db.sh
mvn -B clean verify
