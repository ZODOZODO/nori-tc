@echo off
setlocal EnableExtensions

REM ============================================================================
REM Force UTF-8 console code page for this CMD session so Java/Spring logs with
REM Korean text are rendered correctly when the script is executed in external
REM cmd.exe. (Prevents mojibake caused by CP949/other code pages.)
REM ============================================================================
chcp 65001 >nul

REM ============================================================================
REM Trace launcher for tc-ui-backend-app
REM
REM Current repo status check:
REM   - This script first validates whether tc-ui-backend-app is included in
REM     settings.gradle.kts.
REM   - It also warns if build.gradle.kts does not mention TcUiBackendApplication.
REM
REM Why this precheck exists:
REM   - If tc-ui-backend-app is not included in settings.gradle.kts, root Gradle
REM     cannot execute :apps:tc-ui-backend-app:bootJar.
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%" || goto :ERR_STOP

set "APP_ID=tc-ui-backend-app"
set "APP_TASK=:apps:tc-ui-backend-app:bootJar"
set "APP_DIR=apps\tc-ui-backend-app"
set "APP_DIR_FWD=apps/tc-ui-backend-app"
set "TRACE_ROOT=C:\tc-trace\%APP_ID%"
set "TRACE_HEAP=%TRACE_ROOT%\heap"
set "TRACE_GC=%TRACE_ROOT%\gc"
set "TRACE_JFR=%TRACE_ROOT%\jfr"
set "APP_JAR="
set "FAIL_REASON="
set "CONFIG_DIR=%SCRIPT_DIR%config"

REM ============================================================================
REM spring.config.import override policy:
REM - Shared properties (tc-db/tc-log) are loaded from repo-root config/
REM - UI app properties (tc-messaging/tc-redis/tc-ui-backend) are loaded from
REM   apps/tc-ui-backend-app/config/
REM
REM Important:
REM - tc-ui-backend.properties contains required Kafka topic values, including
REM   tc.ui.backend.kafka.gateway-events-topic.
REM - If that file is omitted from spring.config.import, startup fails during
REM   required property validation in UiKafkaTopicProperties.
REM ============================================================================
set "SPRING_CONFIG_IMPORTS=optional:file:config/tc-db.properties,optional:file:%APP_DIR_FWD%/config/tc-messaging.properties,optional:file:%APP_DIR_FWD%/config/tc-redis.properties,optional:file:config/tc-log.properties,optional:file:%APP_DIR_FWD%/config/tc-ui-backend.properties"

if not exist "%SCRIPT_DIR%gradlew.bat" goto :ERR_NO_GRADLEW
where java >nul 2>&1
if errorlevel 1 goto :ERR_NO_JAVA

findstr /C:"tc-ui-backend-app" "%SCRIPT_DIR%settings.gradle.kts" >nul
if errorlevel 1 (
    set "FAIL_REASON=SETTINGS_MISSING"
    goto :ERR_UI_PRECHECK
)

findstr /C:"TcUiBackendApplication" "%SCRIPT_DIR%apps\tc-ui-backend-app\build.gradle.kts" >nul
if errorlevel 1 echo [WARN] TcUiBackendApplication was not found in ui build.gradle.kts mainClass setting.

REM ============================================================================
REM Secret handling policy (initial development mode):
REM - DB/Redis passwords are currently hardcoded in imported properties files.
REM - Therefore this script does not prompt for secret environment variables.
REM ============================================================================

if not exist "%TRACE_HEAP%" mkdir "%TRACE_HEAP%" >nul 2>&1
if not exist "%TRACE_GC%"   mkdir "%TRACE_GC%"   >nul 2>&1
if not exist "%TRACE_JFR%"  mkdir "%TRACE_JFR%"  >nul 2>&1

if not exist "%TRACE_HEAP%" goto :ERR_TRACE_DIR
if not exist "%TRACE_GC%" goto :ERR_TRACE_DIR
if not exist "%TRACE_JFR%" goto :ERR_TRACE_DIR

echo [INFO] Building %APP_ID% bootJar...
call "%SCRIPT_DIR%gradlew.bat" %APP_TASK% --no-daemon
if errorlevel 1 goto :ERR_BUILD

for /f "delims=" %%F in ('dir /b /a:-d /o:-d "%APP_DIR%\build\libs\*.jar" 2^>nul') do (
    echo(%%~nxF|findstr /I /R /C:"-plain\.jar$" >nul
    if errorlevel 1 if not defined APP_JAR set "APP_JAR=%SCRIPT_DIR%%APP_DIR%\build\libs\%%F"
)

if not defined APP_JAR goto :ERR_NO_JAR

echo [INFO] Jar: %APP_JAR%
echo [INFO] Trace root: %TRACE_ROOT%
echo [INFO] Working dir: %SCRIPT_DIR%
echo [INFO] Config dir (spring.config.import file:config/...): %CONFIG_DIR%
echo [INFO] spring.config.import override: %SPRING_CONFIG_IMPORTS%
echo [INFO] Stop app with Ctrl+C. JFR will be dumped on exit.
echo.

REM ============================================================================
REM Keep the process working directory at repo root.
REM
REM Reason:
REM - Application YAML imports optional:file:config/*.properties.
REM - Spring resolves file:config/... relative to the process working directory.
REM - If we pushd into the app module directory, repo-root config files are not
REM   found and datasource/messaging/redis properties can be silently skipped.
REM ============================================================================
pushd "%SCRIPT_DIR%" >nul 2>&1
if errorlevel 1 goto :ERR_APP_DIR

java ^
  -Duser.timezone=Asia/Seoul ^
  "-Dspring.config.import=%SPRING_CONFIG_IMPORTS%" ^
  -XX:+HeapDumpOnOutOfMemoryError ^
  "-XX:HeapDumpPath=%TRACE_HEAP%" ^
  "-Xlog:gc*,safepoint:file=%TRACE_GC%\%APP_ID%-%%p.log:time,uptime,level,tags:filecount=10,filesize=20m" ^
  "-XX:StartFlightRecording=filename=%TRACE_JFR%\%APP_ID%-%%p.jfr,settings=profile,disk=true,maxage=12h,maxsize=1024m,dumponexit=true" ^
  -jar "%APP_JAR%"

set "APP_EXIT_CODE=%ERRORLEVEL%"
popd
echo.
echo [INFO] %APP_ID% exited with code %APP_EXIT_CODE%.
exit /b %APP_EXIT_CODE%

:ERR_UI_PRECHECK
echo [ERROR] tc-ui-backend-app precheck failed.
if /I "%FAIL_REASON%"=="SETTINGS_MISSING" goto :ERR_UI_SETTINGS_MISSING
goto :ERR_STOP

:ERR_UI_SETTINGS_MISSING
echo [ERROR] tc-ui-backend-app is not included in settings.gradle.kts.
echo [ERROR] Root Gradle build cannot run :apps:tc-ui-backend-app:bootJar in this state.
echo [HINT] Add include and projectDir entries for apps\tc-ui-backend-app in settings.gradle.kts.
echo [HINT] Check springBoot.mainClass in apps\tc-ui-backend-app\build.gradle.kts.
echo [HINT] Expected main class is com.nori.tc.apps.uibackend.TcUiBackendApplication.
goto :ERR_STOP

:ERR_NO_GRADLEW
echo [ERROR] gradlew.bat was not found in repo root.
goto :ERR_STOP

:ERR_NO_JAVA
echo [ERROR] java was not found in PATH.
goto :ERR_STOP

:ERR_TRACE_DIR
echo [ERROR] Failed to create one or more trace directories under %TRACE_ROOT%.
goto :ERR_STOP

:ERR_BUILD
echo [ERROR] bootJar build failed. Check Gradle output above.
goto :ERR_STOP

:ERR_NO_JAR
echo [ERROR] bootJar file was not found in %APP_DIR%\build\libs.
dir /b "%APP_DIR%\build\libs\*.jar" 2>nul
goto :ERR_STOP

:ERR_APP_DIR
echo [ERROR] Failed to change working directory to %SCRIPT_DIR%.
goto :ERR_STOP

:ERR_STOP
echo.
echo [ERROR] %APP_ID% trace launcher stopped.
pause
exit /b 1
