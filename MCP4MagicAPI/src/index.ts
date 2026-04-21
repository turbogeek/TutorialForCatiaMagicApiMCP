import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

//const NWS_API_BASE = "https://api.weather.gov";
const USER_AGENT = "MCP4MagicAPI-app/1.0";

// Create server instance
const server = new McpServer({
  name: "MCP4MagicAPI",
  version: "1.0.0",
});