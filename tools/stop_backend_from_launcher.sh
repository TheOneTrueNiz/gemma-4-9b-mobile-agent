#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="/data/data/com.termux/files/home/gemma-4-mobile-agent"

cd "${ROOT_DIR}"

pkill -f "python.*backend/main.py" >/dev/null 2>&1 || true
pkill -f "${ROOT_DIR}/backend/llama-server.*--port 8888" >/dev/null 2>&1 || true

echo "Gemma backend stop requested."
