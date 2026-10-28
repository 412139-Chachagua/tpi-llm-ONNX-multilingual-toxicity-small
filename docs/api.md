# Contrato REST (v1)

Todos los cuerpos son JSON (`Content-Type: application/json`). Los errores nunca incluyen trazas, rutas ni el texto enviado.

## `POST /api/v1/toxicity/classify`
Petición: `{"text": "..."}` — obligatorio, no vacío, máximo `TOXICITY_MAX_CHARS` caracteres (5000 por defecto).
El texto se trunca a 256 tokens al tokenizar (el resto no influye en el resultado).

```bash
curl -s -X POST http://localhost:8080/api/v1/toxicity/classify \
  -H 'Content-Type: application/json' \
  -d '{"text":"You are a complete idiot and nobody wants you here."}'
```
Respuesta real (200), probabilidades por categoría en [0, 1] (sigmoide sobre los logits del modelo):
```json
{"model":"multilingual-toxicity-small","scores":{"toxicity":0.924,"severe_toxicity":0.045,"obscene":0.174,"threat":0.004,"insult":0.920,"identity_attack":0.016,"sexual_explicit":0.003}}
```
(valores redondeados aquí; la API devuelve doble precisión completa).

| Estado | `error` | Causa |
|---|---|---|
| 400 | `invalid_request` | cuerpo ausente/mal formado, `text` ausente o en blanco |
| 413 | `text_too_long` | más de `TOXICITY_MAX_CHARS` caracteres |
| 415 | `unsupported_media_type` | Content-Type distinto de JSON |
| 405 / 404 | `method_not_allowed` / `not_found` | |
| 503 | `service_busy` | sin capacidad de inferencia dentro del timeout |
| 500 | `inference_failed` / `internal_error` | fallo interno (detalle solo en logs, sin texto del usuario) |

Formato de error: `{"error":"<código>","message":"<descripción genérica>"}`.

## `GET /health/live`
200 `{"status":"UP"}` si el proceso responde.

## `GET /health/ready`
200 `{"status":"UP"}` cuando el modelo está cargado; 503 `{"status":"DOWN"}` en caso contrario.
Si el modelo no puede cargarse, la aplicación no arranca (no llega a atender peticiones).

> Autenticación por API key: se añade en la Fase 5 (hoy el endpoint no la exige).
