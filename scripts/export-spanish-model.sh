#!/usr/bin/env bash
# Obtiene bgonzalezbustamante/bert-spanish-toxicity en la revisión fijada, verifica su SHA-256 y lo exporta a ONNX.
# El repositorio del modelo NO publica ONNX (solo safetensors), por eso se convierte aquí. Requiere Docker.
# Uso: scripts/export-spanish-model.sh [directorio_destino]   (por defecto: models/spanish)
# Descarga ~440 MB; el ONNX resultante (~440 MB, fp32) no se versiona en git (models/ está en .gitignore).
set -euo pipefail

REVISION="fe9d55ff4c871f314b4c8a9878468cd49e36697a"
BASE="https://huggingface.co/bgonzalezbustamante/bert-spanish-toxicity/resolve/${REVISION}"
DEST="${1:-models/spanish}"
HERE="$(cd "$(dirname "$0")" && pwd)"

# archivo|sha256 (ver docs/model-spanish.md)
FILES=(
  "model.safetensors|911bd376efd40f4cff5484f0dc85ec7f956f1c9699751a7a36e11b9c3286b293"
  "tokenizer.json|29f908924ecfc985bb4ddf8b856fd94407f48792b11e069d56d40e8b55ebfb61"
  "config.json|"
  "tokenizer_config.json|"
  "special_tokens_map.json|"
  "vocab.txt|"
)

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

SRC="$(mktemp -d)"; trap 'rm -rf "$SRC"' EXIT
mkdir -p "$DEST"
DEST_ABS="$(cd "$DEST" && pwd)"

for entry in "${FILES[@]}"; do
  file="${entry%%|*}"; expected="${entry##*|}"
  echo "Descargando ${file} ..."
  curl -fSL --no-progress-meter --retry 3 -o "${SRC}/${file}" "${BASE}/${file}"
  if [[ -n "$expected" && "$(sha256 "${SRC}/${file}")" != "$expected" ]]; then
    echo "SHA-256 distinto en ${file}; se aborta." >&2; exit 1
  fi
done

mkdir -p "${SRC}/out"
docker run --rm -v "${SRC}:/work/src" -v "${SRC}/out:/work/out" -v "${HERE}/export-spanish-model.py:/work/export.py:ro" \
  python:3.12-slim bash -c "pip install -q torch --index-url https://download.pytorch.org/whl/cpu && pip install -q transformers onnx && python /work/export.py"

cp "${SRC}/out/model.onnx" "${DEST_ABS}/model.onnx"
cp "${SRC}/tokenizer.json" "${SRC}/config.json" "${DEST_ABS}/"
echo "Listo en ${DEST}. SHA-256 de model.onnx: $(sha256 "${DEST_ABS}/model.onnx")"
