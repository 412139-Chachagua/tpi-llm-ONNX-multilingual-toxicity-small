# Docker

## Construir
```bash
docker build -t toxicity-service:dev .
```
Multi-stage: la etapa `maven` compila (sin JDK/Maven en el anfitrión); la imagen final es `eclipse-temurin:21-jre`, usuario
no-root (uid 10001), ~695 MB. **El modelo no está en la imagen** (no hay descargas en el arranque ni versiones cambiantes):
se monta en `/models` con la estructura de `docs/model.md` (`/models/onnx/model.onnx`, `/models/tokenizer.json`).
La imagen no contiene secretos: `TOXICITY_API_KEY` se pasa en ejecución.

## Ejecutar
```bash
docker run --rm --memory 2g -p 8080:8080 \
  -e TOXICITY_API_KEY='<clave>' \
  -v "$PWD/models:/models:ro" \
  toxicity-service:dev

curl -s -X POST localhost:8080/api/v1/toxicity/classify \
  -H 'Content-Type: application/json' -H 'X-API-Key: <clave>' \
  -d '{"text":"You are a complete idiot and nobody wants you here."}'
```

## Mediciones (Docker Desktop, linux/amd64 sobre Mac Intel, 16 CPUs visibles, `--memory 2g`; una sola ejecución)
| Métrica | Valor |
|---|---|
| Arranque en frío hasta `/health/ready` | ~15,6 s (Spring: 13,7 s) |
| Primera inferencia (HTTP, incluye calentamiento) | ~620 ms |
| Latencia posterior (50 peticiones secuenciales, texto de ~280 caracteres) | mediana 152 ms · p95 386 ms · máx 903 ms |
| Memoria en reposo tras las pruebas | ~1,22 GiB (RSS del proceso ≈ 1,28 GB) |
| Con `--memory 1g` | arranca y `/health/ready` responde UP (sin OOM) en reposo; **no** se probó bajo carga |

Son cifras indicativas de un entorno de desarrollo; no extrapolar a Azure sin medir allí. Con modelo fp32 se recomienda
partir de 2 GiB de memoria; la prueba con 1 GiB solo prueba que arranca.

## Fallos verificados en el contenedor
- Sin modelo montado → el proceso termina con `ModelUnavailableException` (no sirve peticiones).
- Modelo corrupto → termina con error de inicialización del bean del modelo.
- Sin `TOXICITY_API_KEY` → el arranque falla (a menos que `TOXICITY_AUTH_DISABLED=true`).
- Clave incorrecta → 401.

## Limitaciones
- Probado solo en linux/amd64. linux/arm64 debería funcionar (ONNX Runtime y DJL publican nativos), pero **no se verificó**.
- No hay `HEALTHCHECK` en la imagen (la JRE no trae `curl`); usar las sondas `/health/live` y `/health/ready` del orquestador.
