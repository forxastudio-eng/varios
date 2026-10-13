#!/usr/bin/env bash
# Arranca el servidor de partituras en Linux (Debian/Ubuntu). Se instala todo solo la primera vez.
#   ./iniciar-servidor.sh
set -euo pipefail
cd "$(dirname "$0")"
AUDIVERIS_VERSION="5.11.0"
PUERTO="${PORT:-8765}"

if ! command -v python3 >/dev/null; then echo "Falta Python 3. Instálalo con: sudo apt install python3 python3-venv"; exit 1; fi
if [ ! -d .venv ]; then
  echo "· Creando entorno de Python (solo la primera vez)…"
  python3 -m venv .venv
fi
echo "· Comprobando las librerías…"
.venv/bin/pip install -q --disable-pip-version-check -r requirements.txt

if ! find audiveris -iname Audiveris -type f 2>/dev/null | grep -q .; then
  echo "· Descargando Audiveris $AUDIVERIS_VERSION (solo la primera vez, unos 80 MB)…"
  mkdir -p audiveris
  curl -fL --progress-bar -o audiveris/audiveris.deb "https://github.com/Audiveris/audiveris/releases/download/$AUDIVERIS_VERSION/Audiveris-$AUDIVERIS_VERSION-ubuntu22.04-x86_64.deb"
  dpkg-deb -x audiveris/audiveris.deb audiveris/
  rm -f audiveris/audiveris.deb
fi

echo "· Arrancando el servidor. Déjalo abierto mientras importas partituras (Ctrl+C para cerrarlo)."
exec .venv/bin/uvicorn app:app --host 0.0.0.0 --port "$PUERTO"
