#!/bin/bash
set -e

echo "========================================================"
echo "TutorialForCatiaMagicApiMCP Setup"
echo "========================================================"
echo ""

# 1. Check for Node.js
if ! command -v node &> /dev/null; then
    echo "[ERROR] Node.js is not installed or not found in PATH."
    echo "Please install Node.js 20+ from https://nodejs.org/"
    exit 1
fi
echo "[OK] Node.js is installed."

# 2. Configure Java via CATIA Magic / Cameo
echo ""
echo "========================================================"
echo "Java Configuration (CATIA Magic / Cameo)"
echo "========================================================"
echo "The test harness and validation pipeline require a Java runtime. It is highly recommended "
echo "to use the JRE bundled with your CATIA Magic / Cameo Systems Modeler installation."
echo ""

read -p "Enter the full path to your Cameo installation (e.g. /opt/Cameo Systems Modeler) or press ENTER to skip: " CAMEO_DIR

if [ -n "$CAMEO_DIR" ]; then
    # Remove quotes if present
    CAMEO_DIR=$(echo "$CAMEO_DIR" | sed -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//")
    
    JRE_BIN="$CAMEO_DIR/jre/bin/java"
    
    if [ ! -f "$JRE_BIN" ]; then
        echo "[ERROR] Could not find java at '$JRE_BIN'."
        echo "Please ensure the path is correct and points to the root Cameo directory."
    else
        echo "[OK] Found Cameo bundled Java at '$JRE_BIN'."
        read -p "Do you want to set JAVA_HOME and add it to your PATH? (y/n): " SET_JAVA
        if [[ "$SET_JAVA" =~ ^[Yy]$ ]]; then
            echo ""
            echo "To use this Java runtime, add the following lines to your ~/.bashrc or ~/.zshrc:"
            echo "export JAVA_HOME=\"$CAMEO_DIR/jre\""
            echo "export PATH=\"\$JAVA_HOME/bin:\$PATH\""
            echo ""
            echo "You will need to run 'source ~/.bashrc' or restart your terminal for these changes to take effect."
        fi
    fi
else
    echo "[INFO] Skipping Cameo Java configuration. Ensure Java is in your PATH."
fi

# 3. Setup MCP dependencies
echo ""
echo "========================================================"
echo "Installing MCP Server Dependencies..."
echo "========================================================"
if [ -d "MCP4MagicAPI" ]; then
    cd MCP4MagicAPI
    npm install
    
    echo ""
    echo "========================================================"
    echo "Building MCP Server..."
    echo "========================================================"
    npm run build
    cd ..
else
    echo "[ERROR] MCP4MagicAPI directory not found!"
    exit 1
fi

echo ""
echo "========================================================"
echo "Setup Complete!"
echo "========================================================"
echo "You are ready to run the test harness and the MCP server."
echo "To add this MCP server to Claude Code, run the following command:"
echo "claude mcp add MCP4MagicAPI node \"$(pwd)/MCP4MagicAPI/build/index.js\""
echo ""
