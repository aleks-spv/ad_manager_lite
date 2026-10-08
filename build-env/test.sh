#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="${ADM_IMAGE:-adm-build:local}"
LOG_DIR="$REPO_ROOT/build-env/logs"
LOG_FILE="$LOG_DIR/test-$(date +%Y%m%d-%H%M%S).log"

mkdir -p "$LOG_DIR"
mkdir -p "$REPO_ROOT/build-env/home"

docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" "$REPO_ROOT/build-env"

echo "=== Test started: $(date) ==="
echo "Log: $LOG_FILE"
echo ""

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -v "$REPO_ROOT:/work" \
  -w /work \
  -e GRADLE_USER_HOME=/work/build-env/.gradle \
  -e HOME=/work/build-env/home \
  "$IMAGE" \
  gradle testDebugUnitTest --no-daemon --stacktrace "$@" 2>&1 | tee "$LOG_FILE"

EXIT_CODE=${PIPESTATUS[0]}

echo ""
echo "=== Test finished: $(date) ==="
echo "Reports: $REPO_ROOT/app/build/reports/tests/testDebugUnitTest/index.html"

if [ $EXIT_CODE -ne 0 ]; then
    echo "FAILED (exit $EXIT_CODE) — see log: $LOG_FILE"
fi

exit $EXIT_CODE
