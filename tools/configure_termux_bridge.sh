#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

TERMUX_PROPS_DIR="/data/data/com.termux/files/home/.termux"
TERMUX_PROPS_FILE="${TERMUX_PROPS_DIR}/termux.properties"

mkdir -p "${TERMUX_PROPS_DIR}"
touch "${TERMUX_PROPS_FILE}"

python - "${TERMUX_PROPS_FILE}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
lines = path.read_text().splitlines()
out = []
inserted = False

for line in lines:
    stripped = line.strip()
    if stripped.startswith("allow-external-apps") or stripped.startswith("# allow-external-apps"):
        if not inserted:
            out.append("allow-external-apps = true")
            inserted = True
        continue
    out.append(line)

if not inserted:
    out.append("allow-external-apps = true")

path.write_text("\n".join(out) + "\n")
PY

termux-reload-settings
echo "Configured Termux bridge."
grep -n "allow-external-apps" "${TERMUX_PROPS_FILE}"
