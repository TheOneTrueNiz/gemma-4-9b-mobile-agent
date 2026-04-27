#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! ./tools/check_android_launcher_env.sh; then
  echo "Environment check failed. Attempting repo-local Android SDK bootstrap..."
  ./tools/bootstrap_android_sdk.sh
  ./tools/check_android_launcher_env.sh
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${SDK_DIR}" && -d "${ROOT_DIR}/android-sdk" ]]; then
  SDK_DIR="${ROOT_DIR}/android-sdk"
fi
if [[ -z "${SDK_DIR}" && -f "${ROOT_DIR}/local.properties" ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' "${ROOT_DIR}/local.properties" | head -n 1)"
fi

if [[ -n "${SDK_DIR}" ]]; then
  export ANDROID_SDK_ROOT="${SDK_DIR}"
  export ANDROID_HOME="${SDK_DIR}"
  printf 'sdk.dir=%s\n' "${SDK_DIR}" > "${ROOT_DIR}/local.properties"
fi

GRADLE_ARGS=()
HOST_ARCH="$(uname -m)"
if [[ "${HOST_ARCH}" != "x86_64" && "${HOST_ARCH}" != "amd64" ]]; then
  if ! command -v aapt2 >/dev/null 2>&1; then
    echo "No host-native aapt2 found for ${HOST_ARCH}."
    echo "Install it first, for example on Debian-like systems: apt-get install aapt"
    exit 1
  fi

  HOST_AAPT2="$(command -v aapt2)"
  echo "Using host aapt2 override: ${HOST_AAPT2}"
  GRADLE_ARGS+=("-Pandroid.aapt2FromMavenOverride=${HOST_AAPT2}")
fi

./gradlew "${GRADLE_ARGS[@]}" :app:assembleDebug
