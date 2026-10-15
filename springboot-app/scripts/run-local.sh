#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$APP_DIR"

if [[ ! -f .env ]]; then
  echo 'Missing .env. Run ./scripts/create-dev-env.sh first.' >&2
  exit 1
fi
if [[ ! -f target/integration-process-bootstrap.jar ]]; then
  echo 'Missing packaged JAR. Run ./scripts/start-local.sh first.' >&2
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a
export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
exec java -jar target/integration-process-bootstrap.jar
