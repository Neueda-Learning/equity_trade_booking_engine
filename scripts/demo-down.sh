#!/usr/bin/env bash

set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly PROJECT_NAME="equity-demo"

cd "$PROJECT_ROOT"

mapfile -t container_ids < <(
  docker compose -p "$PROJECT_NAME" ps -aq
)
for container_id in "${container_ids[@]}"; do
  [[ -z "$container_id" ]] && continue
  actual_project="$(docker inspect --format \
    '{{ index .Config.Labels "com.docker.compose.project" }}' \
    "$container_id")"
  if [[ "$actual_project" != "$PROJECT_NAME" ]]; then
    echo "Refusing to delete container from project: $actual_project" >&2
    exit 2
  fi
done

mapfile -t volume_names < <(
  docker volume ls \
    --filter "label=com.docker.compose.project=$PROJECT_NAME" \
    --format '{{.Name}}'
)
for volume_name in "${volume_names[@]}"; do
  if [[ "$volume_name" != "${PROJECT_NAME}_"* ]]; then
    echo "Refusing to delete unexpected volume: $volume_name" >&2
    exit 2
  fi
done

echo "Removing only Compose project $PROJECT_NAME and its isolated volumes."
docker compose -p "$PROJECT_NAME" down -v --remove-orphans
