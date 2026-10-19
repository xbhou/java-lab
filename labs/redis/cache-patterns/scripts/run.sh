#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose up -d

echo "Waiting for Redis health check..."
until [ "$(docker inspect -f '{{.State.Health.Status}}' java-lab-redis-cache 2>/dev/null || true)" = "healthy" ]; do
  sleep 1
done

mvn -q clean compile exec:java   -Dexec.mainClass=dev.xbhou.javalab.redis.cache.CachePatternsApp
