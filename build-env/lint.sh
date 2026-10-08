#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${ADM_IMAGE:-adm-build:local}"

mkdir -p "$REPO_ROOT/build-env/home"

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$REPO_ROOT/build-env"

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "$REPO_ROOT:/work" \
  -w /work \
  -e GRADLE_USER_HOME=/work/build-env/.gradle \
  -e HOME=/work/build-env/home \
  "$IMAGE" \
  gradle lintDebug --no-daemon --stacktrace "$@"

echo "Reports: $REPO_ROOT/app/build/reports/lint-results-debug.html"
