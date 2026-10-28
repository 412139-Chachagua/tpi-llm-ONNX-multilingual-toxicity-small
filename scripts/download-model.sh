#!/usr/bin/env bash
# Descarga los artefactos del modelo en la revisión fijada y verifica su SHA-256.
# Uso: scripts/download-model.sh [directorio_destino]   (por defecto: models)
# Idempotente: si un archivo ya existe con el hash correcto, no se vuelve a descargar.
set -euo pipefail

REVISION="01adcc628a33848569816d4851a38544c321b92c"
BASE="https://huggingface.co/Horizon-Labs/multilingual-toxicity-small/resolve/${REVISION}"
DEST="${1:-models}"

# archivo|sha256 (ver docs/model.md)
FILES=(
  "onnx/model.onnx|ad02a9f395860db6586c6803210b529a402f90b2d1bd8663e1a4d82c975cf3e9"
  "tokenizer.json|4379fe7f4a7af10b1ac15ffc8ae90f446f92082393699e9dd54b8c3b5ec0abad"
)

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"; expected="${entry##*|}"; target="${DEST}/${file}"
  mkdir -p "$(dirname "$target")"
  if [[ -f "$target" && "$(sha256 "$target")" == "$expected" ]]; then
    echo "OK (ya presente) ${file}"; continue
  fi
  echo "Descargando ${file} ..."
  curl -fSL --no-progress-meter --retry 3 -o "${target}.part" "${BASE}/${file}"
  actual="$(sha256 "${target}.part")"
  if [[ "$actual" != "$expected" ]]; then
    rm -f "${target}.part"
    echo "ERROR: SHA-256 incorrecto para ${file} (esperado ${expected}, obtenido ${actual})" >&2
    exit 1
  fi
  mv "${target}.part" "$target"
  echo "OK ${file}"
done
