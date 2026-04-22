@echo off
REM Universal harness wrapper for Windows cmd / PowerShell. Uses curl
REM (bundled with Windows 10+). No Node dependency.
REM
REM Usage:
REM   harness.bat health
REM   harness.bat status
REM   harness.bat log [SINCE]
REM   harness.bat run SCRIPT_PATH [WAIT_MS]
REM   harness.bat stop [REASON]
REM   harness.bat stop-harness
REM   harness.bat raw METHOD PATH [BODY]
REM
REM Env:
REM   HARNESS_HOST (default 127.0.0.1)
REM   HARNESS_PORT (default 8765)

setlocal ENABLEDELAYEDEXPANSION

if "%HARNESS_HOST%"=="" set "HARNESS_HOST=127.0.0.1"
if "%HARNESS_PORT%"=="" set "HARNESS_PORT=8765"
set "BASE=http://%HARNESS_HOST%:%HARNESS_PORT%"

set "CMD=%~1"
if "%CMD%"=="" goto :usage

if /I "%CMD%"=="health"        goto :health
if /I "%CMD%"=="status"        goto :status
if /I "%CMD%"=="log"           goto :log
if /I "%CMD%"=="run"           goto :run
if /I "%CMD%"=="stop"          goto :stop
if /I "%CMD%"=="stop-harness"  goto :stop_harness
if /I "%CMD%"=="raw"           goto :raw

echo unknown command: %CMD% 1>&2
goto :usage

:health
curl -sS -X GET -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/health"
goto :end

:status
curl -sS -X GET -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/status"
goto :end

:log
if "%~2"=="" (
  curl -sS -X GET -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/log"
) else (
  curl -sS -X GET -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/log?since=%~2"
)
goto :end

:run
if "%~2"=="" (
  echo run: SCRIPT_PATH required 1>&2
  goto :end
)
set "SCRIPT=%~2"
set "WAIT=%~3"
if "%WAIT%"=="" set "WAIT=0"
REM Escape backslashes and quotes in SCRIPT for JSON embedding.
set "SCRIPT=!SCRIPT:\=\\!"
set "SCRIPT=!SCRIPT:"=\"!"
curl -sS -X POST -H "Content-Type: application/json" ^
  --data "{\"scriptPath\":\"!SCRIPT!\",\"waitMs\":%WAIT%}" ^
  -w "^%% HTTP %%{http_code}" --max-time 60 "%BASE%/run"
goto :end

:stop
set "REASON=%~2"
if "%REASON%"=="" set "REASON=explicit stop"
curl -sS -X POST -H "Content-Type: application/json" ^
  --data "{\"reason\":\"!REASON!\"}" ^
  -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/stop"
goto :end

:stop_harness
curl -sS -X POST -H "Content-Type: application/json" --data "{}" ^
  -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%/stop-harness"
goto :end

:raw
if "%~3"=="" (
  curl -sS -X "%~2" -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%%~3"
) else (
  curl -sS -X "%~2" -H "Content-Type: application/json" --data "%~4" ^
    -w "^%% HTTP %%{http_code}" --max-time 30 "%BASE%%~3"
)
goto :end

:usage
echo usage: %~nx0 ^<health^|status^|log^|run^|stop^|stop-harness^|raw^> ... 1>&2
exit /b 2

:end
endlocal
