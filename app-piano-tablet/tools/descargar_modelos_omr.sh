#!/usr/bin/env bash
# Descarga los tres modelos ONNX del lector de partituras (homr, AGPL-3.0) a la carpeta de «assets» de la app.
# El APK los lleva dentro (≈ 150 MB) y funciona sin internet. Uso: tools/descargar_modelos_omr.sh [carpeta_destino]
set -euo pipefail
DEST="${1:-$(dirname "$0")/../android/app/src/main/assets/omr}"
BASE="https://github.com/liebharc/homr/releases/download/onnx_checkpoints"
mkdir -p "$DEST"
fetch() { # nombre_remoto nombre_local
  local zip="$1.zip" tmp
  if [ -s "$DEST/$2" ]; then echo "ya está: $2"; return; fi
  tmp="$(mktemp -d)"
  curl -fsSL --retry 4 -o "$tmp/$zip" "$BASE/$zip"
  unzip -q -o "$tmp/$zip" -d "$tmp"
  local onnx
  onnx="$(find "$tmp" -name '*.onnx' | head -1)"
  mv "$onnx" "$DEST/$2"
  rm -rf "$tmp"
  echo "descargado: $2 ($(du -h "$DEST/$2" | cut -f1))"
}
fetch "segnet_308-3296ccd40960f90ca6ab9c035cca945675d30a0f" segnet.onnx
fetch "encoder_pytorch_model_396-f6feedb42ff90087d898b0941a55d040fa6b2903" encoder.onnx
fetch "decoder_pytorch_model_396-f6feedb42ff90087d898b0941a55d040fa6b2903" decoder.onnx
