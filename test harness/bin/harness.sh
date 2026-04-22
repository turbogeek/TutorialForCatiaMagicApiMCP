#!/usr/bin/env bash
# Universal harness wrapper. Uses curl so there's no Node dependency.
#
# Usage:
#   harness.sh health
#   harness.sh status
#   harness.sh log [SINCE]
#   harness.sh run <SCRIPT_PATH> [WAIT_MS]
#   harness.sh stop [REASON]
#   harness.sh stop-harness
#   harness.sh raw <METHOD> <PATH> [BODY]          (escape hatch)
#
# Env:
#   HARNESS_HOST (default 127.0.0.1)
#   HARNESS_PORT (default 8765)

set -u
HOST="${HARNESS_HOST:-127.0.0.1}"
PORT="${HARNESS_PORT:-8765}"
BASE="http://${HOST}:${PORT}"

die() { echo "$*" >&2; exit 2; }

need() { [ $# -ge 1 ] || die "missing argument"; }

call() {
  # call METHOD PATH [BODY]
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    curl -sS -X "$method" -H 'Content-Type: application/json' --data "$body" \
      -w '\nHTTP %{http_code}\n' --max-time 30 "${BASE}${path}"
  else
    curl -sS -X "$method" -w '\nHTTP %{http_code}\n' --max-time 30 "${BASE}${path}"
  fi
}

# Minimal JSON string escape for scriptPath etc.
jesc() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

cmd="${1:-}"
[ -n "$cmd" ] || die "usage: $0 <health|status|log|run|stop|stop-harness|raw> ..."
shift

case "$cmd" in
  health)  call GET /health ;;
  status)  call GET /status ;;
  log)
    if [ $# -ge 1 ]; then
      call GET "/log?since=$1"
    else
      call GET /log
    fi
    ;;
  run)
    need "$@"
    script_path="$1"
    wait_ms="${2:-0}"
    body='{"scriptPath":"'"$(jesc "$script_path")"'","waitMs":'"$wait_ms"'}'
    call POST /run "$body"
    ;;
  stop)
    reason="${1:-explicit stop}"
    body='{"reason":"'"$(jesc "$reason")"'"}'
    call POST /stop "$body"
    ;;
  stop-harness)
    call POST /stop-harness '{}'
    ;;
  raw)
    # raw METHOD PATH [BODY]
    [ $# -ge 2 ] || die "raw: METHOD PATH [BODY]"
    call "$1" "$2" "${3:-}"
    ;;
  *)
    die "unknown command: $cmd"
    ;;
esac
