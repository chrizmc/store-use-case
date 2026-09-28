#!/usr/bin/env bash
# Stops everything scripts/start-demo.sh starts: backend, emulator, and the background
# services (Neo4j, Ollama, Postgres) — since nohup+disown means closing the terminal
# does NOT stop them on its own.
# Usage: scripts/stop-demo.sh                 (stops backend + emulator only)
#        FULL=1 scripts/stop-demo.sh          (also stops Postgres/Neo4j/Ollama)
set -uo pipefail
cd "$(dirname "$0")/.."

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
FULL="${FULL:-0}"

echo "==> Stopping backend"
pkill -f "tsx watch src/index.ts" 2>/dev/null && echo "    stopped" || echo "    not running"

echo "==> Stopping screen mirroring (scrcpy)"
pkill -f "scrcpy -s" 2>/dev/null && echo "    stopped" || echo "    not running"

echo "==> Stopping emulator"
if "$ADB" devices 2>/dev/null | grep -q "device$"; then
  "$ADB" emu kill 2>/dev/null && echo "    stopped" || echo "    could not stop via adb"
else
  echo "    not running"
fi

if [[ "$FULL" == "1" ]]; then
  echo "==> Stopping Ollama"
  pkill -f "ollama serve" 2>/dev/null && echo "    stopped" || echo "    not running"

  echo "==> Stopping Neo4j"
  neo4j stop 2>/dev/null || echo "    not running"

  echo "==> Stopping Postgres"
  brew services stop postgresql@17 > /dev/null 2>&1 || echo "    not running"
else
  echo "==> Leaving Postgres/Neo4j/Ollama running (pass FULL=1 to stop those too)"
fi

echo "==> Done"
