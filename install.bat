@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo TutorialForCatiaMagicApiMCP Setup
echo ========================================================
echo.

:: 1. Check for Node.js
where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js is not installed or not found in PATH.
    echo Please install Node.js 20+ from https://nodejs.org/
    exit /b 1
)
echo [OK] Node.js is installed.

:: 2. Configure Java via CATIA Magic / Cameo
echo.
echo ========================================================
echo Java Configuration (CATIA Magic / Cameo)
echo ========================================================
echo The test harness and validation pipeline require a Java runtime. It is highly recommended 
echo to use the JRE bundled with your CATIA Magic / Cameo Systems Modeler installation.
echo.

set /p CAMEO_DIR="Enter the full path to your Cameo installation (e.g. C:\Program Files\Cameo Systems Modeler) or press ENTER to skip: "

if "%CAMEO_DIR%"=="" (
    echo [INFO] Skipping Cameo Java configuration. Ensure Java is in your PATH.
    goto :npm_setup
)

:: Remove surrounding quotes if present
set CAMEO_DIR=%CAMEO_DIR:"=%

set JRE_BIN=%CAMEO_DIR%\jre\bin\java.exe

if not exist "%JRE_BIN%" (
    echo [ERROR] Could not find java.exe at "%JRE_BIN%".
    echo Please ensure the path is correct and points to the root Cameo directory.
    goto :npm_setup
)

echo [OK] Found Cameo bundled Java at "%JRE_BIN%".

set /p SET_JAVA="Do you want to set JAVA_HOME and add it to your PATH? (y/n): "
if /i "!SET_JAVA!"=="y" (
    :: Set JAVA_HOME permanently for the user
    setx JAVA_HOME "%CAMEO_DIR%\jre"
    
    :: Add to User PATH if not already present
    echo [INFO] Adding Java to User PATH...
    for /f "skip=2 tokens=3*" %%a in ('reg query HKCU\Environment /v PATH') do set USER_PATH=%%a %%b
    echo !USER_PATH! | find /i "%CAMEO_DIR%\jre\bin" > nul
    if !ERRORLEVEL! neq 0 (
        setx PATH "%CAMEO_DIR%\jre\bin;!USER_PATH!"
        echo [OK] Added Java to PATH.
    ) else (
        echo [INFO] Java is already in your PATH.
    )
    
    echo [OK] JAVA_HOME has been set. You MUST restart your terminal for these changes to take effect.
)

:npm_setup
echo.
echo ========================================================
echo Installing MCP Server Dependencies...
echo ========================================================
if exist "MCP4MagicAPI" (
    cd MCP4MagicAPI
    call npm install
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] npm install failed.
        exit /b 1
    )
    echo.
    echo ========================================================
    echo Building MCP Server...
    echo ========================================================
    call npm run build
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] npm run build failed.
        exit /b 1
    )
    cd ..
) else (
    echo [ERROR] MCP4MagicAPI directory not found!
    exit /b 1
)

echo.
echo ========================================================
echo Setup Complete!
echo ========================================================
echo You are ready to run the test harness and the MCP server.
echo To add this MCP server to Claude Code, run the following command:
echo claude mcp add MCP4MagicAPI node "%~dp0MCP4MagicAPI\build\index.js"
echo.
pause
