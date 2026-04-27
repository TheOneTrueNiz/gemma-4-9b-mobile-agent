#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

APK_PATH="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "${APK_PATH}" ]]; then
  echo "Debug APK not found. Building it first..."
  ./tools/build_android_launcher.sh
fi

INSTALL_APK_PATH="${APK_PATH}"
if [[ -d "${HOME}/storage/downloads" ]]; then
  INSTALL_APK_PATH="${HOME}/storage/downloads/gemma-launcher-debug.apk"
  cp -f "${APK_PATH}" "${INSTALL_APK_PATH}"
  echo "Copied APK to shared downloads: ${INSTALL_APK_PATH}"
elif [[ -d "/sdcard/Download" ]]; then
  INSTALL_APK_PATH="/sdcard/Download/gemma-launcher-debug.apk"
  cp -f "${APK_PATH}" "${INSTALL_APK_PATH}"
  echo "Copied APK to shared downloads: ${INSTALL_APK_PATH}"
fi

if command -v termux-open >/dev/null 2>&1; then
  echo "Opening Android package installer..."
  termux-open "${INSTALL_APK_PATH}"
else
  echo "APK ready at: ${INSTALL_APK_PATH}"
fi

cat <<EOF

After installation:
  1. Open Android Settings
  2. Go to Default apps
  3. Set Home app to Gemma Launcher
EOF
