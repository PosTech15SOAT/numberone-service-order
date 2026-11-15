#!/usr/bin/env bash
set -euo pipefail
image="$1"
name="smoke-${GITHUB_RUN_ID:-local}-$$"
network="$name"
cleanup() {
  docker rm -f "$name-app" "$name-db" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker network create "$network" >/dev/null
docker run -d --name "$name-db" --network "$network" --network-alias postgres \
  -e POSTGRES_DB=smoke -e POSTGRES_USER=smoke -e POSTGRES_PASSWORD=smoke-local-only \
  postgres:17-alpine >/dev/null
for attempt in {1..30}; do
  if docker exec "$name-db" pg_isready -h 127.0.0.1 -U smoke -d smoke >/dev/null; then break; fi
  if [[ "$attempt" == 30 ]]; then echo "PostgreSQL indisponível no smoke." >&2; exit 1; fi
  sleep 1
done
docker run -d --name "$name-app" --network "$network" -p 127.0.0.1::8080 \
  --read-only --tmpfs /tmp:rw,size=128m --cap-drop ALL --security-opt no-new-privileges \
  -e DB_URL=jdbc:postgresql://postgres:5432/smoke -e DB_USERNAME=smoke -e DB_PASSWORD=smoke-local-only \
  -e OS_BRANCH_ID=40000000-0000-4000-8000-000000000001 \
  -e DD_TRACE_ENABLED=false -e DD_INSTRUMENTATION_TELEMETRY_ENABLED=false "$image" >/dev/null
jar="$(mktemp)"
trap 'rm -f "$jar"; cleanup' EXIT
docker cp "$name-app:/app/app.jar" "$jar"
python3 scripts/check-image-contracts.py "$jar"
port="$(docker port "$name-app" 8080/tcp | cut -d: -f2)"
for attempt in {1..45}; do
  if curl -fsS --max-time 3 "http://127.0.0.1:$port/actuator/health/readiness" 2>/dev/null | jq -e '.status == "UP"' >/dev/null; then
    curl -fsS --max-time 3 "http://127.0.0.1:$port/actuator/health/liveness" | jq -e '.status == "UP"' >/dev/null
    curl -fsS --max-time 3 "http://127.0.0.1:$port/actuator/health" | jq -e '.status == "UP"' >/dev/null
    echo "Smoke HTTP da imagem validada concluído."
    exit 0
  fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$name-app")" != true ]]; then break; fi
  sleep 2
done
docker logs --tail 40 "$name-app" >&2
exit 1
