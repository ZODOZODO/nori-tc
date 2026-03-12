#!/usr/bin/env bash

set -u
set -o pipefail

# ------------------------------------------------------------
# Trace launcher for tc-comm-gateway-app (WSL/Linux)
#
# Execution flow:
# 1) Build bootJar.
# 2) Run java -jar from repo root.
# 3) Collect heap/gc/jfr traces.
# ------------------------------------------------------------

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT" || {
  echo "[ERROR] Failed to move to repo root directory: $REPO_ROOT"
  exit 1
}

APP_ID="tc-comm-gateway-app"
APP_TASK=":apps:tc-comm-gateway-app:bootJar"
APP_DIR="apps/tc-comm-gateway-app"
TRACE_BASE="${TC_TRACE_BASE:-$HOME/tc-trace}"
TRACE_ROOT="${TRACE_BASE}/${APP_ID}"
TRACE_HEAP="${TRACE_ROOT}/heap"
TRACE_GC="${TRACE_ROOT}/gc"
TRACE_JFR="${TRACE_ROOT}/jfr"
TRACE_CONSOLE="${TRACE_ROOT}/console"
TRACE_TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
TRACE_STDOUT_LOG="${TRACE_CONSOLE}/${APP_ID}-${TRACE_TIMESTAMP}-stdout.log"
TRACE_STDERR_LOG="${TRACE_CONSOLE}/${APP_ID}-${TRACE_TIMESTAMP}-stderr.log"
CONFIG_DIR="${REPO_ROOT}/config"
SPRING_CONFIG_IMPORTS="optional:file:config/tc-db.properties,optional:file:${APP_DIR}/config/tc-messaging.properties,optional:file:${APP_DIR}/config/tc-redis.properties,optional:file:config/tc-log.properties,optional:file:${APP_DIR}/config/tc-comm.properties"

# Optional Netty leak detection: export TC_NETTY_LEAK_DETECTION_LEVEL=advanced
NETTY_LEAK_OPTS=()
if [[ -n "${TC_NETTY_LEAK_DETECTION_LEVEL:-}" ]]; then
  NETTY_LEAK_OPTS+=("-Dio.netty.leakDetection.level=${TC_NETTY_LEAK_DETECTION_LEVEL}")
fi

if [[ ! -f "$REPO_ROOT/gradlew" ]]; then
  echo "[ERROR] gradlew was not found in repo root."
  exit 1
fi

if [[ ! -x "$REPO_ROOT/gradlew" ]]; then
  chmod +x "$REPO_ROOT/gradlew" || {
    echo "[ERROR] Failed to grant execute permission to gradlew."
    exit 1
  }
fi

if ! command -v java >/dev/null 2>&1; then
  echo "[ERROR] java was not found in PATH."
  exit 1
fi

if ! command -v tee >/dev/null 2>&1; then
  echo "[ERROR] tee was not found in PATH."
  exit 1
fi

mkdir -p "$TRACE_HEAP" "$TRACE_GC" "$TRACE_JFR" "$TRACE_CONSOLE" || {
  echo "[ERROR] Failed to create trace directories under $TRACE_ROOT."
  exit 1
}

: > "$TRACE_STDOUT_LOG" || {
  echo "[ERROR] Failed to initialize stdout trace log: $TRACE_STDOUT_LOG"
  exit 1
}

: > "$TRACE_STDERR_LOG" || {
  echo "[ERROR] Failed to initialize stderr trace log: $TRACE_STDERR_LOG"
  exit 1
}

# 실행 스크립트와 애플리케이션의 표준 출력/표준 에러를 모두 파일에도 남깁니다.
exec > >(tee -a "$TRACE_STDOUT_LOG") 2> >(tee -a "$TRACE_STDERR_LOG" >&2)

echo "[INFO] Stdout trace log: $TRACE_STDOUT_LOG"
echo "[INFO] Stderr trace log: $TRACE_STDERR_LOG"
echo "[INFO] Building ${APP_ID} bootJar..."
"$REPO_ROOT/gradlew" "$APP_TASK" --no-daemon || {
  echo "[ERROR] bootJar build failed."
  exit 1
}

APP_JAR=""
while IFS= read -r jar_path; do
  [[ "$jar_path" == *-plain.jar ]] && continue
  APP_JAR="$jar_path"
  break
done < <(ls -1t "$APP_DIR"/build/libs/*.jar 2>/dev/null || true)

if [[ -z "$APP_JAR" ]]; then
  echo "[ERROR] bootJar file was not found in $APP_DIR/build/libs."
  ls -1 "$APP_DIR"/build/libs/*.jar 2>/dev/null || true
  exit 1
fi

echo "[INFO] Jar: $APP_JAR"
echo "[INFO] Trace root: $TRACE_ROOT"
echo "[INFO] Working dir: $REPO_ROOT"
echo "[INFO] Config dir (spring.config.import file:config/...): $CONFIG_DIR"
echo "[INFO] spring.config.import override: $SPRING_CONFIG_IMPORTS"
echo "[INFO] Stop app with Ctrl+C. JFR will be dumped on exit."
echo

java \
  -Duser.timezone=Asia/Seoul \
  "-Dspring.config.import=${SPRING_CONFIG_IMPORTS}" \
  "${NETTY_LEAK_OPTS[@]}" \
  -XX:+HeapDumpOnOutOfMemoryError \
  "-XX:HeapDumpPath=${TRACE_HEAP}" \
  "-Xlog:gc*,safepoint:file=${TRACE_GC}/${APP_ID}-%p.log:time,uptime,level,tags:filecount=10,filesize=20m" \
  "-XX:StartFlightRecording=filename=${TRACE_JFR}/${APP_ID}-%p.jfr,settings=profile,disk=true,maxage=12h,maxsize=1024m,dumponexit=true" \
  -jar "$APP_JAR"

APP_EXIT_CODE=$?
echo
echo "[INFO] ${APP_ID} exited with code ${APP_EXIT_CODE}."
exit "$APP_EXIT_CODE"
