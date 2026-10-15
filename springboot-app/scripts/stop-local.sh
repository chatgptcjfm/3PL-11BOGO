#!/usr/bin/env bash
set -Eeuo pipefail
pm2 delete integration-process-bootstrap >/dev/null 2>&1 || true
if command -v fuser >/dev/null 2>&1; then
  fuser -k 3000/tcp 2>/dev/null || true
fi
echo 'Stopped integration-process-bootstrap.'
