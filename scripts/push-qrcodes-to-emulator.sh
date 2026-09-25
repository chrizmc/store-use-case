#!/usr/bin/env bash
# Pushes every generated shelf QR PNG into the emulator's Pictures gallery and
# triggers a media scan, so they show up in the Photos app for the "Upload QR
# photo" flow. Safe to re-run any time new QR codes are generated.
set -euo pipefail

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QR_DIR="$SCRIPT_DIR/../backend/public/qrcodes"
DEVICE_DIR="/sdcard/Pictures/BopisQrCodes"

if [ ! -d "$QR_DIR" ] || [ -z "$(ls -A "$QR_DIR"/*.png 2>/dev/null)" ]; then
  echo "No QR codes found at $QR_DIR — run 'npm run qrcodes:generate' in backend/ first." >&2
  exit 1
fi

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "No adb device/emulator connected — start the emulator first." >&2
  exit 1
fi

"$ADB" shell mkdir -p "$DEVICE_DIR"

for file in "$QR_DIR"/*.png; do
  name="$(basename "$file")"
  "$ADB" push "$file" "$DEVICE_DIR/$name" >/dev/null
  echo "pushed $name"
done

# Media scanner must be told about each new file individually, or the gallery
# app won't index them until the emulator is rebooted.
for file in "$QR_DIR"/*.png; do
  name="$(basename "$file")"
  "$ADB" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file://$DEVICE_DIR/$name" >/dev/null
done

echo "Done: QR codes are now in the emulator's gallery under Pictures/BopisQrCodes."
