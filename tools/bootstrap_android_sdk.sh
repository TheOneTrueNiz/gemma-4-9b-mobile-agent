#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-${ROOT_DIR}/android-sdk}}"
CMDLINE_ZIP_URL="https://dl.google.com/android/repository/commandlinetools-linux-14742923_latest.zip"
CMDLINE_ZIP_PATH="${SDK_DIR}/cmdline-tools.zip"
CMDLINE_ROOT="${SDK_DIR}/cmdline-tools"
LATEST_DIR="${CMDLINE_ROOT}/latest"

mkdir -p "${SDK_DIR}" "${CMDLINE_ROOT}"

if ! command -v java >/dev/null 2>&1; then
  echo "Java 17+ is required before bootstrapping the Android SDK."
  exit 1
fi

if [[ ! -x "${LATEST_DIR}/bin/sdkmanager" ]]; then
  echo "Downloading Android command-line tools..."
  curl -L "${CMDLINE_ZIP_URL}" -o "${CMDLINE_ZIP_PATH}"
  rm -rf "${LATEST_DIR}" "${CMDLINE_ROOT}/cmdline-tools"
  unzip -q "${CMDLINE_ZIP_PATH}" -d "${CMDLINE_ROOT}"
  mkdir -p "${LATEST_DIR}"
  shopt -s dotglob
  mv "${CMDLINE_ROOT}/cmdline-tools/"* "${LATEST_DIR}/"
  shopt -u dotglob
  rm -rf "${CMDLINE_ROOT}/cmdline-tools" "${CMDLINE_ZIP_PATH}"
fi

export ANDROID_SDK_ROOT="${SDK_DIR}"
export PATH="${LATEST_DIR}/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

echo "Accepting Android SDK licenses..."
set +o pipefail
yes | sdkmanager --licenses >/dev/null
set -o pipefail

echo "Installing required Android SDK packages..."
sdkmanager \
  "platform-tools" \
  "platforms;android-34" \
  "build-tools;34.0.0"

if [[ ! -f "${ROOT_DIR}/local.properties" ]]; then
  printf 'sdk.dir=%s\n' "${ANDROID_SDK_ROOT}" > "${ROOT_DIR}/local.properties"
fi

echo "Android SDK bootstrapped at: ${ANDROID_SDK_ROOT}"
