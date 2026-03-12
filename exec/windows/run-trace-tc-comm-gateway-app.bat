@echo off
setlocal EnableExtensions

REM ============================================================================
REM Force UTF-8 console code page for this CMD session so Java/Spring logs with
REM Korean text are rendered correctly when the script is executed in external
REM cmd.exe. (Prevents mojibake caused by CP949/other code pages.)
REM ============================================================================
chcp 65001 >nul

REM ============================================================================
REM Trace launcher for tc-comm-gateway-app
REM
REM What this script does:
REM   1) Build Spring Boot bootJar for the gateway app.
REM   2) Run the app by java -jar outside VS Code debug session.
REM   3) Collect JFR, GC logs, and HeapDump on OOM under C:\tc-trace.
REM
REM Why this script exists:
REM   - It separates app runtime from VS Code Java debugger / language server.
REM   - It keeps trace artifacts for later analysis after long-running tests.
REM
REM Output folders:
REM   C:\tc-trace\tc-comm-gateway-app\jfr
REM   C:\tc-trace\tc-comm-gateway-app\gc
REM   C:\tc-trace\tc-comm-gateway-app\heap
REM ============================================================================

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"
cd /d "%REPO_ROOT%" || goto :ERR_GENERIC

set "APP_ID=tc-comm-gateway-app"
set "APP_TASK=:apps:tc-comm-gateway-app:bootJar"
set "APP_DIR=apps\tc-comm-gateway-app"
set "APP_DIR_FWD=apps/tc-comm-gateway-app"
set "TRACE_ROOT=C:\tc-trace\%APP_ID%"
set "TRACE_HEAP=%TRACE_ROOT%\heap"
set "TRACE_GC=%TRACE_ROOT%\gc"
set "TRACE_JFR=%TRACE_ROOT%\jfr"
set "TRACE_CONSOLE=%TRACE_ROOT%\console"
set "APP_JAR="
set "NETTY_LEAK_OPTS="
set "CONFIG_DIR=%REPO_ROOT%\config"
set "SPRING_CONFIG_IMPORTS=optional:file:config/tc-db.properties,optional:file:%APP_DIR_FWD%/config/tc-messaging.properties,optional:file:%APP_DIR_FWD%/config/tc-redis.properties,optional:file:config/tc-log.properties,optional:file:%APP_DIR_FWD%/config/tc-comm.properties"
set "POWERSHELL_EXE="
set "POWERSHELL_HELPER=%SCRIPT_DIR%run-java-with-console-logs.ps1"

REM Optional Netty leak detection. Disabled by default because it adds overhead.
REM set "NETTY_LEAK_OPTS=-Dio.netty.leakDetection.level=advanced"

if not exist "%REPO_ROOT%\gradlew.bat" goto :ERR_NO_GRADLEW
where java >nul 2>&1
if errorlevel 1 goto :ERR_NO_JAVA
where powershell >nul 2>&1
if not errorlevel 1 set "POWERSHELL_EXE=powershell"
if not defined POWERSHELL_EXE (
    where pwsh >nul 2>&1
    if not errorlevel 1 set "POWERSHELL_EXE=pwsh"
)
if not defined POWERSHELL_EXE goto :ERR_NO_POWERSHELL
if not exist "%POWERSHELL_HELPER%" goto :ERR_NO_POWERSHELL_HELPER

REM ============================================================================
REM Secret handling policy (initial development mode):
REM - DB/Redis passwords are currently hardcoded in imported properties files.
REM - Therefore this script does not prompt for secret environment variables.
REM ============================================================================

if not exist "%TRACE_HEAP%" mkdir "%TRACE_HEAP%" >nul 2>&1
if not exist "%TRACE_GC%"   mkdir "%TRACE_GC%"   >nul 2>&1
if not exist "%TRACE_JFR%"  mkdir "%TRACE_JFR%"  >nul 2>&1
if not exist "%TRACE_CONSOLE%" mkdir "%TRACE_CONSOLE%" >nul 2>&1

if not exist "%TRACE_HEAP%" goto :ERR_TRACE_DIR
if not exist "%TRACE_GC%" goto :ERR_TRACE_DIR
if not exist "%TRACE_JFR%" goto :ERR_TRACE_DIR
if not exist "%TRACE_CONSOLE%" goto :ERR_TRACE_DIR

for /f %%I in ('%POWERSHELL_EXE% -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TRACE_TIMESTAMP=%%I"
if not defined TRACE_TIMESTAMP goto :ERR_TRACE_TIMESTAMP
set "TRACE_STDOUT_LOG=%TRACE_CONSOLE%\%APP_ID%-%TRACE_TIMESTAMP%-stdout.log"
set "TRACE_STDERR_LOG=%TRACE_CONSOLE%\%APP_ID%-%TRACE_TIMESTAMP%-stderr.log"
set "JAVA_ARGS_FILE=%TRACE_CONSOLE%\%APP_ID%-%TRACE_TIMESTAMP%-java-args.txt"

type nul > "%TRACE_STDOUT_LOG%" 2>nul
if errorlevel 1 goto :ERR_TRACE_LOG_INIT
type nul > "%TRACE_STDERR_LOG%" 2>nul
if errorlevel 1 goto :ERR_TRACE_LOG_INIT

echo [INFO] Building %APP_ID% bootJar...
call "%REPO_ROOT%\gradlew.bat" %APP_TASK% --no-daemon
if errorlevel 1 goto :ERR_BUILD

REM Find bootJar and skip plain jar if Gradle generated both.
REM
REM Why this implementation:
REM   - The script now keeps delayed expansion disabled globally so secret values
REM     containing '!' are preserved when read from environment/prompt input.
REM   - Use findstr suffix matching to filter out "-plain.jar" artifacts.
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
echo [INFO] Stdout trace log: %TRACE_STDOUT_LOG%
echo [INFO] Stderr trace log: %TRACE_STDERR_LOG%
echo [INFO] Stop app with Ctrl+C. JFR will be dumped on exit.
echo.

REM ============================================================================
REM Keep the process working directory at repo root.
REM
REM Reason:
REM - apps/tc-comm-gateway-app/src/main/resources/application.yaml imports
REM   optional:file:config/*.properties
REM - Spring resolves "file:config/..." relative to the process working directory.
REM - If we pushd into apps\tc-comm-gateway-app, imports point to
REM   apps\tc-comm-gateway-app\config\... (missing), so DB URL is not loaded and
REM   DataSource auto-configuration fails with "url attribute is not specified".
REM ============================================================================
pushd "%REPO_ROOT%" >nul 2>&1
if errorlevel 1 goto :ERR_APP_DIR

(
  echo -Duser.timezone=Asia/Seoul
  echo -Dspring.config.import=%SPRING_CONFIG_IMPORTS%
  if defined NETTY_LEAK_OPTS echo %NETTY_LEAK_OPTS%
  echo -XX:+HeapDumpOnOutOfMemoryError
  echo -XX:HeapDumpPath=%TRACE_HEAP%
  echo -Xlog:gc*,safepoint:file=%TRACE_GC%\%APP_ID%-%%p.log:time,uptime,level,tags:filecount=10,filesize=20m
  echo -XX:StartFlightRecording=filename=%TRACE_JFR%\%APP_ID%-%%p.jfr,settings=profile,disk=true,maxage=12h,maxsize=1024m,dumponexit=true
  echo -jar
  echo %APP_JAR%
) > "%JAVA_ARGS_FILE%"
if errorlevel 1 goto :ERR_TRACE_LOG_INIT

call %POWERSHELL_EXE% -NoProfile -ExecutionPolicy Bypass -File "%POWERSHELL_HELPER%" -WorkingDirectory "%REPO_ROOT%" -StdoutLogPath "%TRACE_STDOUT_LOG%" -StderrLogPath "%TRACE_STDERR_LOG%" -ArgumentFilePath "%JAVA_ARGS_FILE%"

set "APP_EXIT_CODE=%ERRORLEVEL%"
if exist "%JAVA_ARGS_FILE%" del /q "%JAVA_ARGS_FILE%" >nul 2>&1
popd
echo.
echo [INFO] %APP_ID% exited with code %APP_EXIT_CODE%.
exit /b %APP_EXIT_CODE%

:ERR_NO_GRADLEW
echo [ERROR] gradlew.bat was not found in repo root.
goto :ERR_GENERIC

:ERR_NO_JAVA
echo [ERROR] java was not found in PATH.
goto :ERR_GENERIC

:ERR_NO_POWERSHELL
echo [ERROR] powershell or pwsh was not found in PATH.
goto :ERR_GENERIC

:ERR_NO_POWERSHELL_HELPER
echo [ERROR] PowerShell helper script was not found: %POWERSHELL_HELPER%
goto :ERR_GENERIC

:ERR_TRACE_DIR
echo [ERROR] Failed to create one or more trace directories under %TRACE_ROOT%.
goto :ERR_GENERIC

:ERR_TRACE_TIMESTAMP
echo [ERROR] Failed to create trace timestamp for console log files.
goto :ERR_GENERIC

:ERR_TRACE_LOG_INIT
echo [ERROR] Failed to initialize console trace log files under %TRACE_CONSOLE%.
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
echo [ERROR] %APP_ID% trace launcher stopped.
pause
exit /b 1
