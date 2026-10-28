# Modelo y artefactos (Fase 2)

## Procedencia
- Repositorio: https://huggingface.co/Horizon-Labs/multilingual-toxicity-small
- **Revisión fijada:** `01adcc628a33848569816d4851a38544c321b92c`
- **Licencia:** Apache-2.0 (declarada en el model card y en los metadatos). Base: `jhu-clsp/mmBERT-small`;
  datos de entrenamiento: Civil Comments (CC0) + traducciones. Los textos de entrenamiento no se redistribuyen aquí.
- Arquitectura: `ModernBertForSequenceClassification`, 141M parámetros, multi-label, 34 idiomas.

## Artefactos usados
| Archivo | Tamaño (bytes) | SHA-256 (publicado por HF y verificado localmente) |
|---|---|---|
| `onnx/model.onnx` (fp32) | 563 109 174 | `ad02a9f395860db6586c6803210b529a402f90b2d1bd8663e1a4d82c975cf3e9` |
| `tokenizer.json` | 34 363 286 | `4379fe7f4a7af10b1ac15ffc8ae90f446f92082393699e9dd54b8c3b5ec0abad` |
| `config.json`, `tokenizer_config.json`, `special_tokens_map.json` | — | (HF no publica hash LFS; son archivos pequeños no-LFS) |

Existe además `onnx/model_quantized.onnx` (int8, 268 MB, sha256 `44d470dc…f0080`); el model card indica una diferencia media de
0.0009 en probabilidad. **No descargado**: decisión pendiente (ver abajo).

Descarga reproducible con verificación de SHA-256 (idempotente, ignora archivos ya correctos):
```bash
scripts/download-model.sh [directorio]   # por defecto: models/ (ignorado por git)
```
Los hashes y la revisión están fijados en el propio script, que debe mantenerse sincronizado con esta tabla.

## Interfaz ONNX (inspeccionada con ONNX Runtime 1.23.0)
| Tensor | Dirección | Tipo | Forma |
|---|---|---|---|
| `input_ids` | entrada | INT64 | `[batch, seq]` |
| `attention_mask` | entrada | INT64 | `[batch, seq]` |
| `logits` | salida | FLOAT | `[batch, 7]` |

No hay `token_type_ids`. Orden de la salida (`config.json` `id2label`):
0 `toxicity`, 1 `severe_toxicity`, 2 `obscene`, 3 `threat`, 4 `insult`, 5 `identity_attack`, 6 `sexual_explicit`.
Coincide con las siete categorías del plan.

## Semántica de la salida
El grafo exportado (`code/train/export_onnx.py`) devuelve `.logits` crudos, y el model card indica
"independent probability (sigmoid)" por etiqueta (`problem_type: multi_label_classification`).
**Por tanto el servicio debe aplicar sigmoide por categoría** (no softmax) → rango (0, 1).

## Tokenizer
- `tokenizer.json` (BPE, normalizer `Replace " "→"▁"`, Metaspace), plantilla `<bos> texto <eos>` (ids 2 y 1),
  `pad_token_id` 0, truncamiento a **256 tokens** (entrenado con max length 256; el modelo admite hasta 8192 posiciones).
- Para un único texto no hace falta padding; `attention_mask` es todo 1.
- Opción de tokenización en Java: DJL `ai.djl.huggingface:tokenizers` 0.39.0 (binding JNI del tokenizer Rust de HF, lee `tokenizer.json` tal cual).
  Su equivalencia con el tokenizer de referencia se verificará en la Fase 3.

## Compatibilidad de ONNX Runtime
- ORT Java **1.31.1 no incluye librería nativa para macOS Intel (osx-x64)**; la última versión con ese nativo es **1.23.0**
  (1.24.0 no existe en Maven Central). 1.23.0 sí soporta linux-x64, linux-aarch64, osx-aarch64 y win-x64.
- Se fija 1.23.0 en el `pom.xml`: carga el modelo e inspecciona los tensores correctamente en este equipo.

## Dependencias de inferencia (Fase 3)
- `ai.djl.huggingface:tokenizers` 0.39.0 (Apache-2.0): tokeniza con `tokenizer.json`. Trae nativos para linux-x86_64,
  linux-aarch64, osx-aarch64 y win-x86_64. **Ninguna versión (0.30–0.39) incluye macOS Intel**, por lo que los tests con
  el modelo real se ejecutan en contenedor Linux en ese equipo (comando en el README).
- Verificado en contenedor `maven:3.9-eclipse-temurin-21` (linux-x86_64): carga del modelo fp32, truncamiento a 256 tokens,
  BOS/EOS, siete categorías en [0,1], determinismo, textos en varios idiomas/emojis; ofensivo > benigno en `toxicity` e `insult`.
