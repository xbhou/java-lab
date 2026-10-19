#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d

echo "Waiting for MySQL health check..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' java-lab-mysql-index 2>/dev/null || true)" = "healthy" ]; do
  sleep 1
done

for file in sql/*.sql; do
  echo
  echo "============================================================"
  echo "Running $file"
  echo "============================================================"
  docker compose exec -T mysql     mysql -uroot -proot < "$file"
done
