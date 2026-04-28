#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT_DIR="/data/data/com.termux/files/home/gemma-4-mobile-agent"
HEALTH_URL="http://127.0.0.1:1337/health"
LOG_PATH="${ROOT_DIR}/backend_server.log"

cd "${ROOT_DIR}"

backend_http_ready() {
  curl -fsS --max-time 2 "${HEALTH_URL}" >/dev/null 2>&1
}

actor_ready() {
  local payload
  payload="$(curl -fsS --max-time 2 "${HEALTH_URL}" 2>/dev/null)" || return 1
  python -c 'import json, sys; payload = json.loads(sys.argv[1]); raise SystemExit(0 if payload.get("status") == "ok" and payload.get("actor_online") else 1)' "${payload}"
}

wait_for_actor_ready() {
  local attempts="${1:-45}"
  for _ in $(seq 1 "${attempts}"); do
    if actor_ready; then
      echo "Gemma backend healthy."
      return 0
    fi
    sleep 2
  done
  return 1
}

stop_backend() {
  pkill -f "python.*backend/main.py" >/dev/null 2>&1 || true
  pkill -f "${ROOT_DIR}/backend/llama-server.*--port 8888" >/dev/null 2>&1 || true
}

if [[ "${1:-}" == "--restart" ]]; then
  stop_backend
  sleep 1
fi

if actor_ready; then
  echo "Gemma backend already healthy."
  exit 0
fi

if backend_http_ready; then
  echo "Gemma API is up. Waiting for actor readiness."
  if wait_for_actor_ready 30; then
    exit 0
  fi
  echo "Gemma API came up without a ready actor. Restarting backend."
  stop_backend
  sleep 1
fi

nohup python backend/main.py >"${LOG_PATH}" 2>&1 &

if wait_for_actor_ready 60; then
  exit 0
fi

echo "Gemma backend start requested but actor health check did not pass yet."
