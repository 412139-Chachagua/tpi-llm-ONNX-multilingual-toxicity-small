# Modelo español: `bgonzalezbustamante/bert-spanish-toxicity`

Segundo clasificador, independiente de `Horizon-Labs/multilingual-toxicity-small` (ver [model.md](model.md)).

- **Origen:** <https://huggingface.co/bgonzalezbustamante/bert-spanish-toxicity>, revisión `fe9d55ff4c871f314b4c8a9878468cd49e36697a`.
- **Licencia:** MIT (metadatos del repo). Base: `dccuchile/bert-base-spanish-wwm-cased`.
- **Arquitectura:** `BertForSequenceClassification`, 12 capas, hidden 768, vocabulario 31002, 2 etiquetas
  (`id2label`: `0=NONTOXIC`, `1=TOXIC`; el servicio las lee de `config.json`, no las supone).
- **El repo NO publica ONNX**, solo `model.safetensors` (439 433 208 B, fp32). Se exporta con `scripts/export-spanish-model.sh`
  (PyTorch → ONNX, opset 17, fp32, ejes dinámicos). SHA-256 verificados de la fuente:
  `model.safetensors` `911bd376…b293`, `tokenizer.json` `29f90892…ebfb61`.
- **ONNX exportado:** entradas `input_ids`, `attention_mask`, `token_type_ids` (int64 `[batch, seq]`), salida `logits` float `[batch, 2]`.
  La exportación no es necesariamente idéntica byte a byte entre ejecuciones; la verificación es numérica (ver abajo).
- **Tokenizer:** `tokenizer.json` del modelo con DJL (cased, sin `strip_accents`, `[CLS]=4`, `[SEP]=5`, `[PAD]=1`), truncado a
  `TOXICITY_SPANISH_MAX_TOKENS` (256), sin padding. Los IDs coinciden con `transformers` en los casos probados.
- **Salida:** softmax sobre los 2 logits. Probabilidades del modelo, no calibradas.
- **Cuantización:** ninguna (fp32). No se evaluó int8; si se adopta, medir la diferencia de probabilidades contra fp32.

## Obtención (no se versiona: `models/` está en `.gitignore`)
```bash
scripts/export-spanish-model.sh        # descarga ~440 MB, exporta con Docker; deja models/spanish/{model.onnx,tokenizer.json,config.json}
```
Variables: `TOXICITY_SPANISH_MODEL_PATH`, `TOXICITY_SPANISH_TOKENIZER_PATH`, `TOXICITY_SPANISH_CONFIG_PATH`,
`TOXICITY_SPANISH_MAX_TOKENS`, `TOXICITY_SPANISH_MAX_CONCURRENT`. Se carga **una sola vez** al arrancar; sin descargas en tiempo de ejecución.
Si falta o es inválido, la aplicación no arranca (igual que el modelo original).

## Verificación numérica
Logits de PyTorch (transformers 5.x, CPU) contra ONNX Runtime 1.23.0 en 3 textos (corto tóxico, corto no tóxico y uno truncado a 256 tokens
con emoji): coinciden hasta ~1e-6 (ver `SpanishOnnxModelRealTest`, tolerancia 1e-3). Es una muestra pequeña, no una evaluación de calidad del modelo.

## Memoria (medición puntual)
Programa de prueba (ONNX Runtime + DJL, sin Spring) en contenedor de 1 vCPU / 2 GiB, `MaxRAMPercentage=70`: RSS ≈ 1030 MB con el
modelo original, ≈ 1525 MB con ambos, ≈ 1547 MB tras 20 pasadas. La aplicación Spring suma overhead propio: **margen estrecho dentro de 2 GiB**;
confirmar con la app real en Azure antes de desplegar. Latencia con 1 vCPU, 256 tokens: ~5 s (original) y ~7 s (español) en una corrida en frío de Docker en Mac.

## Rendimiento en CPU limitada
`SpanishOnnxModel` fija los hilos de ONNX Runtime a `Runtime.availableProcessors()` (la JVM respeta el límite de vCPU del contenedor;
configurable con `TOXICITY_SPANISH_INTRA_OP_THREADS`) y hace una inferencia de arranque (calentamiento y verificación). Medición en
contenedor con `--cpus=1`, mismo texto corto (11 tokens), 5 requests: antes 1,5–1,8 s; después 0,08–0,10 s (0,36 s la primera).
Una sola máquina (Mac Intel con Docker), no Azure: confirmar allí.

## Despliegue
La imagen `Dockerfile.azure` actual solo empaqueta el modelo original: para desplegar esta rama hay que incluir `models/spanish/` en la imagen
(o montarlo) y fijar las variables anteriores; sin ellos la aplicación no arrancará. No se modificó nada de Azure en esta tarea.
