#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${ADM_IMAGE:-adm-build:local}"
LOG_DIR="$REPO_ROOT/build-env/logs"
LOG_FILE="$LOG_DIR/build-$(date +%Y%m%d-%H%M%S).log"

mkdir -p "$LOG_DIR"

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$REPO_ROOT/build-env"

echo "=== Build started: $(date) ==="
echo "Log: $LOG_FILE"
echo ""

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "$REPO_ROOT:/work" \
  -w /work \
  -e GRADLE_USER_HOME=/work/build-env/.gradle \
  "$IMAGE" \
  gradle assembleDebug --no-daemon --stacktrace --info "$@" 2>&1 | tee "$LOG_FILE"

EXIT_CODE=${PIPESTATUS[0]}

echo ""
echo "=== Build finished: $(date) ==="

if [ $EXIT_CODE -eq 0 ]; then
    echo "OK — APK: $REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
else
    echo "FAILED (exit $EXIT_CODE) — see log: $LOG_FILE"
fi

exit $EXIT_CODE
