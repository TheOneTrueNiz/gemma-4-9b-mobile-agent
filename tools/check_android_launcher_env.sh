#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

missing=0

echo "Checking Android launcher build environment in: $ROOT_DIR"
echo "Host architecture: $(uname -m)"

if ! command -v java >/dev/null 2>&1; then
  echo "Missing: Java 17+"
  missing=1
else
  echo "Java:"
  java -version 2>&1 | sed 's/^/  /'
  java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.version = //p' | head -n 1 | cut -d. -f1)"
  if [[ -n "${java_major}" && "${java_major}" -lt 17 ]]; then
    echo "Java is too old. Need Java 17 or newer."
    missing=1
  fi
fi

SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "${SDK_DIR}" && -d "${ROOT_DIR}/android-sdk" ]]; then
  SDK_DIR="${ROOT_DIR}/android-sdk"
fi
if [[ -z "${SDK_DIR}" && -f local.properties ]]; then
  SDK_DIR="$(sed -n 's/^sdk.dir=//p' local.properties | head -n 1)"
fi

if [[ -z "${SDK_DIR}" ]]; then
  echo "Missing: ANDROID_SDK_ROOT (or local.properties sdk.dir)"
  missing=1
else
  echo "Android SDK: $SDK_DIR"
  if [[ ! -d "${SDK_DIR}" ]]; then
    echo "Missing: SDK directory does not exist"
    missing=1
  fi
fi

host_arch="$(uname -m)"
if [[ "${host_arch}" != "x86_64" && "${host_arch}" != "amd64" ]]; then
  if ! command -v aapt2 >/dev/null 2>&1; then
    echo "Missing: host-native aapt2 for ${host_arch}"
    missing=1
  else
    echo "Host aapt2: $(command -v aapt2)"
  fi
fi

if [[ $missing -ne 0 ]]; then
  cat <<'EOF'

Required minimums for this launcher project:
  - JDK 17 or newer
  - Android SDK with API 34 / Build Tools 34.0.0
  - On non-x86_64 Linux hosts: a host-native aapt2 binary

Recommended next steps:
  1. Install Java 17
  2. Install Android Studio or Android command-line tools
  3. Set ANDROID_SDK_ROOT or create local.properties with sdk.dir=...
  4. On arm64 / aarch64 Debian-like hosts, install: apt-get install aapt
  5. Run ./gradlew :app:assembleDebug
EOF
  exit 1
fi

echo "Environment basics look present."
