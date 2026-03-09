#!/usr/bin/env bash

set -u
set -o pipefail

# ------------------------------------------------------------
# Trace launcher for tc-ui-backend-app (WSL/Linux)
#
# Execution flow:
# 1) Validate project/module preconditions.
# 2) Build bootJar via Gradle wrapper.
# 3) Run java -jar and collect heap/gc/jfr traces.
# ------------------------------------------------------------

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT" || {
  echo "[ERROR] Failed to move to repo root directory: $REPO_ROOT"
  exit 1
}

APP_ID="tc-ui-backend-app"
APP_TASK=":apps:tc-ui-backend-app:bootJar"
APP_DIR="apps/tc-ui-backend-app"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
TRACE_BASE="${TC_TRACE_BASE:-$HOME/tc-trace}"
TRACE_ROOT="${TRACE_BASE}/${APP_ID}"
TRACE_HEAP="${TRACE_ROOT}/heap"
TRACE_GC="${TRACE_ROOT}/gc"
TRACE_JFR="${TRACE_ROOT}/jfr"
CONFIG_DIR="${REPO_ROOT}/config"
SPRING_CONFIG_IMPORTS="optional:file:config/tc-db.properties,optional:file:${APP_DIR}/config/tc-messaging.properties,optional:file:${APP_DIR}/config/tc-redis.properties,optional:file:config/tc-log.properties,optional:file:${APP_DIR}/config/tc-ui-backend.properties,optional:file:${APP_DIR}/config/tc-ui-backend-${SPRING_PROFILES_ACTIVE}.properties"

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

# Root settings precheck. Root Gradle task cannot run when module include is missing.
if ! grep -Fq "tc-ui-backend-app" "$REPO_ROOT/settings.gradle.kts"; then
  echo "[ERROR] tc-ui-backend-app is not included in settings.gradle.kts."
  echo "[ERROR] Root Gradle build cannot run :apps:tc-ui-backend-app:bootJar in this state."
  echo "[HINT] Add include/projectDir entries for apps/tc-ui-backend-app in settings.gradle.kts."
  echo "[HINT] Check springBoot.mainClass in apps/tc-ui-backend-app/build.gradle.kts."
  echo "[HINT] Expected main class: com.nori.tc.apps.uibackend.TcUiBackendApplication"
  exit 1
fi

if ! grep -Fq "TcUiBackendApplication" "$REPO_ROOT/apps/tc-ui-backend-app/build.gradle.kts"; then
  echo "[WARN] TcUiBackendApplication was not found in apps/tc-ui-backend-app/build.gradle.kts mainClass setting."
fi

mkdir -p "$TRACE_HEAP" "$TRACE_GC" "$TRACE_JFR" || {
  echo "[ERROR] Failed to create trace directories under $TRACE_ROOT."
  exit 1
}

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
echo "[INFO] Active profile: $SPRING_PROFILES_ACTIVE"
echo "[INFO] spring.config.import override: $SPRING_CONFIG_IMPORTS"
echo "[INFO] Stop app with Ctrl+C. JFR will be dumped on exit."
echo

# Keep working directory at repo root so file:config/... resolves correctly.
java \
  -Duser.timezone=Asia/Seoul \
  "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE}" \
  "-Dspring.config.import=${SPRING_CONFIG_IMPORTS}" \
  -XX:+HeapDumpOnOutOfMemoryError \
  "-XX:HeapDumpPath=${TRACE_HEAP}" \
  "-Xlog:gc*,safepoint:file=${TRACE_GC}/${APP_ID}-%p.log:time,uptime,level,tags:filecount=10,filesize=20m" \
  "-XX:StartFlightRecording=filename=${TRACE_JFR}/${APP_ID}-%p.jfr,settings=profile,disk=true,maxage=12h,maxsize=1024m,dumponexit=true" \
  -jar "$APP_JAR"

APP_EXIT_CODE=$?
echo
echo "[INFO] ${APP_ID} exited with code ${APP_EXIT_CODE}."
exit "$APP_EXIT_CODE"
