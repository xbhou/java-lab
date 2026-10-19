#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d

echo "Waiting for MySQL health check..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' java-lab-mysql-index 2>/dev/null || true)" = "healthy" ]; do
  sleep 1
done

docker compose exec -T mysql   mysql -uroot -proot < sql/00_setup.sql
