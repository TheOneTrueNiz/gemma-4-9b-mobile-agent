#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="/data/data/com.termux/files/home/gemma-4-mobile-agent"
HEALTH_URL="http://127.0.0.1:1337/health"
LOG_PATH="${ROOT_DIR}/backend_server.log"

cd "${ROOT_DIR}"

is_healthy() {
  curl -fsS --max-time 2 "${HEALTH_URL}" >/dev/null 2>&1
}

stop_backend() {
  pkill -f "python.*backend/main.py" >/dev/null 2>&1 || true
  pkill -f "${ROOT_DIR}/backend/llama-server.*--port 8888" >/dev/null 2>&1 || true
}

if [[ "${1:-}" == "--restart" ]]; then
  stop_backend
  sleep 1
fi

if is_healthy; then
  echo "Gemma backend already healthy."
  exit 0
fi

nohup python backend/main.py >"${LOG_PATH}" 2>&1 &

for _ in $(seq 1 45); do
  if is_healthy; then
    echo "Gemma backend healthy."
    exit 0
  fi
  sleep 1
done

echo "Gemma backend start requested but health check did not pass yet."
