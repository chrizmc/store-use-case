#!/usr/bin/env bash
# One overall command: brings up services, resets to a clean demo state (rules/master
# data seeded, orders empty), starts the backend, and builds/boots the Android app.
# Every step here is also runnable standalone (see README) if you only need to redo one part.
# Usage: scripts/start-demo.sh            (does everything)
#        SKIP_ANDROID=1 scripts/start-demo.sh   (backend/data only, skip emulator+build)
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@25}"
SDK_ROOT="${SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
AVD_NAME="${AVD_NAME:-bopis_demo}"
SKIP_ANDROID="${SKIP_ANDROID:-0}"
ADB="$HOME/Library/Android/sdk/platform-tools/adb"

echo "==> Starting background services (postgres, neo4j, ollama)"
brew services start postgresql@17 > /dev/null
neo4j status > /dev/null 2>&1 || neo4j start
pgrep -f "ollama serve" > /dev/null || (nohup ollama serve > /tmp/ollama.log 2>&1 & disown)

# Stop any previously running backend first — its open Postgres connections would
# otherwise block the drop/recreate below with "database is being accessed by other users".
pkill -f "tsx watch src/index.ts" 2>/dev/null || true
sleep 1

echo "==> Resetting demo data (clean DB + graph; rules/master data seeded, orders empty)"
(cd backend && npm run demo:reset)

echo "==> Starting backend"
(cd backend && nohup npm run dev < /dev/null > /tmp/backend.log 2>&1 & disown)
for _ in $(seq 1 30); do
  curl -sf localhost:3000/health > /dev/null && break
  sleep 1
done
curl -sf localhost:3000/health > /dev/null || { echo "backend did not become healthy, check /tmp/backend.log"; exit 1; }
echo "    backend healthy"

if [[ "$SKIP_ANDROID" == "1" ]]; then
  echo "==> Skipping Android (SKIP_ANDROID=1)"
else
  export JAVA_HOME

  if ! "$ADB" devices | grep -q "device$"; then
    echo "==> Booting emulator ($AVD_NAME)"
    nohup "$HOME/Library/Android/sdk/emulator/emulator" -avd "$AVD_NAME" \
      -no-boot-anim -no-snapshot > /tmp/emulator.log 2>&1 & disown
    "$ADB" wait-for-device
    until "$ADB" shell getprop sys.boot_completed 2>/dev/null | grep -q 1; do sleep 2; done
  fi
  echo "    emulator ready"

  echo "==> Building + installing app"
  (cd android && gradle assembleDebug)
  "$ADB" install -r android/app/build/outputs/apk/debug/app-debug.apk
  "$ADB" shell am start -n com.bopis.associate/.MainActivity
fi

cat <<'EOF'

==> Everything is up. Open:
    http://localhost:3000/simulator.html
    http://localhost:3000/rules.html
    http://localhost:7474              (Neo4j Browser, neo4j / bopispassword)
    tail -f /tmp/backend.log
EOF
