@echo off
setlocal EnableExtensions

REM ============================================================================
REM Force UTF-8 console code page for this CMD session so Java/Spring logs with
REM Korean text are rendered correctly when the script is executed in external
REM cmd.exe. (Prevents mojibake caused by CP949/other code pages.)
REM ============================================================================
chcp 65001 >nul

REM ============================================================================
REM Trace launcher for tc-business-app request
REM
REM Important mapping:
REM   - Requested name: tc-business-app
REM   - Actual module in this repo: apps\tc-business-core-app
REM
REM What this script does:
REM   1) Build bootJar for apps\tc-business-core-app.
REM   2) Run the app by java -jar outside VS Code debug session.
REM   3) Collect JFR, GC logs, and HeapDump on OOM under C:\tc-trace\tc-business-app.
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%" || goto :ERR_GENERIC

set "DISPLAY_APP_ID=tc-business-app"
set "MODULE_APP_ID=tc-business-core-app"
set "APP_TASK=:apps:tc-business-core-app:bootJar"
set "APP_DIR=apps\tc-business-core-app"
set "APP_DIR_FWD=apps/tc-business-core-app"
set "TRACE_ROOT=C:\tc-trace\%DISPLAY_APP_ID%"
set "TRACE_HEAP=%TRACE_ROOT%\heap"
set "TRACE_GC=%TRACE_ROOT%\gc"
set "TRACE_JFR=%TRACE_ROOT%\jfr"
set "APP_JAR="
set "NETTY_LEAK_OPTS="
set "CONFIG_DIR=%REPO_ROOT%\config"
set "SPRING_CONFIG_IMPORTS=optional:file:config/tc-db.properties,optional:file:%APP_DIR_FWD%/config/tc-messaging.properties,optional:file:%APP_DIR_FWD%/config/tc-redis.properties,optional:file:config/tc-log.properties,optional:file:%APP_DIR_FWD%/config/tc-business-core.properties"

if not exist "%REPO_ROOT%\gradlew.bat" goto :ERR_NO_GRADLEW
where java >nul 2>&1
if errorlevel 1 goto :ERR_NO_JAVA

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

echo [INFO] Building %DISPLAY_APP_ID% using module %MODULE_APP_ID% ...
call "%REPO_ROOT%\gradlew.bat" %APP_TASK% --no-daemon
if errorlevel 1 goto :ERR_BUILD

for /f "delims=" %%F in ('dir /b /a:-d /o:-d "%APP_DIR%\build\libs\*.jar" 2^>nul') do (
    echo(%%~nxF|findstr /I /R /C:"-plain\.jar$" >nul
    if errorlevel 1 if not defined APP_JAR set "APP_JAR=%REPO_ROOT%\%APP_DIR%\build\libs\%%F"
)

if not defined APP_JAR goto :ERR_NO_JAR

echo [INFO] Jar: %APP_JAR%
echo [INFO] Trace root: %TRACE_ROOT%
echo [INFO] Working dir: %REPO_ROOT%
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
pushd "%REPO_ROOT%" >nul 2>&1
if errorlevel 1 goto :ERR_APP_DIR

java ^
  -Duser.timezone=Asia/Seoul ^
  "-Dspring.config.import=%SPRING_CONFIG_IMPORTS%" ^
  %NETTY_LEAK_OPTS% ^
  -XX:+HeapDumpOnOutOfMemoryError ^
  "-XX:HeapDumpPath=%TRACE_HEAP%" ^
  "-Xlog:gc*,safepoint:file=%TRACE_GC%\%DISPLAY_APP_ID%-%%p.log:time,uptime,level,tags:filecount=10,filesize=20m" ^
  "-XX:StartFlightRecording=filename=%TRACE_JFR%\%DISPLAY_APP_ID%-%%p.jfr,settings=profile,disk=true,maxage=12h,maxsize=1024m,dumponexit=true" ^
  -jar "%APP_JAR%"

set "APP_EXIT_CODE=%ERRORLEVEL%"
popd
echo.
echo [INFO] %DISPLAY_APP_ID% exited with code %APP_EXIT_CODE%.
exit /b %APP_EXIT_CODE%

:ERR_NO_GRADLEW
echo [ERROR] gradlew.bat was not found in repo root.
goto :ERR_GENERIC

:ERR_NO_JAVA
echo [ERROR] java was not found in PATH.
goto :ERR_GENERIC

:ERR_TRACE_DIR
echo [ERROR] Failed to create one or more trace directories under %TRACE_ROOT%.
goto :ERR_GENERIC

:ERR_BUILD
echo [ERROR] bootJar build failed. Check Gradle output above.
goto :ERR_GENERIC

:ERR_NO_JAR
echo [ERROR] bootJar file was not found in %APP_DIR%\build\libs.
dir /b "%APP_DIR%\build\libs\*.jar" 2>nul
goto :ERR_GENERIC

:ERR_APP_DIR
echo [ERROR] Failed to change working directory to %REPO_ROOT%.
goto :ERR_GENERIC

:ERR_GENERIC
echo.
echo [ERROR] %DISPLAY_APP_ID% trace launcher stopped.
pause
exit /b 1
