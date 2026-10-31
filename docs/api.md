# Contrato REST (v1)

Todos los cuerpos son JSON (`Content-Type: application/json`). Los errores nunca incluyen trazas, rutas ni el texto enviado.

## `POST /api/v1/toxicity/classify`
Petición: `{"text": "..."}` — obligatorio, **debe ser un string JSON** (un número, booleano, arreglo u objeto devuelve 400), no vacío, máximo `TOXICITY_MAX_CHARS` caracteres (5000 por defecto).
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

## `POST /api/v1/toxicity/spanish/classify`
Segundo clasificador, **independiente** del anterior: usa exclusivamente `bgonzalezbustamante/bert-spanish-toxicity`
(ver [model-spanish.md](model-spanish.md)). Misma autenticación (`X-API-Key`), mismo request y mismos errores que `/classify`.

```bash
curl -s -X POST http://localhost:8080/api/v1/toxicity/spanish/classify \
  -H 'Content-Type: application/json' -H "X-API-Key: $TOXICITY_API_KEY" \
  -d '{"text":"Sos un inútil, no servís para nada"}'
```
```json
{"model":"bert-spanish-toxicity","revision":"fe9d55ff4c871f314b4c8a9878468cd49e36697a","label":"TOXIC","scores":{"NONTOXIC":0.19,"TOXIC":0.81}}
```
- `scores`: **softmax** de los 2 logits del modelo (clase única; suman 1). Son las probabilidades del modelo, **no calibradas**.
- `label`: la clase con mayor probabilidad (`TOXIC` o `NONTOXIC`); el orden de clases se lee de `id2label` del modelo.
- Respuesta distinta a la del endpoint original (7 categorías con sigmoide): no se combinan ni se comparan automáticamente.
- Errores: 400 `invalid_request` (texto ausente, vacío, no string o JSON inválido), 413 `text_too_long`, 401, 415, 405,
  500 `inference_failed`, 503 `service_busy`. Texto máximo: el mismo `TOXICITY_MAX_CHARS`.

## `GET /health/live`
200 `{"status":"UP"}` si el proceso responde.

## `GET /health/ready`
200 `{"status":"UP"}` cuando el modelo está cargado; 503 `{"status":"DOWN"}` en caso contrario.
Si alguno de los dos modelos no puede cargarse, la aplicación no arranca (no llega a atender peticiones); por eso `/health/ready` cubre ambos.

## Autenticación y límites
- `/api/**` exige la cabecera `X-API-Key` con el valor de `TOXICITY_API_KEY` (comparación en tiempo constante). Sin ella: 401
  `{"error":"unauthorized",...}`. `/health/*` no requiere clave.
- Sin `TOXICITY_API_KEY` la aplicación **no arranca**, salvo `TOXICITY_AUTH_DISABLED=true` (solo desarrollo local).
- Cuerpo máximo: `TOXICITY_MAX_CHARS × 6 + 1024` bytes (peor caso JSON escapado); más → 413, tanto si se declara `Content-Length` como si el cuerpo es chunked y se supera durante la lectura.
- Concurrencia: máximo `TOXICITY_MAX_CONCURRENT` inferencias simultáneas; una petición espera hasta `TOXICITY_INFERENCE_TIMEOUT`
  por capacidad y, si no la obtiene, recibe 503 `service_busy`.
- Limitación conocida: ese timeout cubre la espera por capacidad, **no** interrumpe una inferencia ya iniciada (una inferencia de
  ≤256 tokens dura milisegundos a decenas de ms en CPU; se medirá en la Fase 6).
- El texto analizado, la API key y las cabeceras no se registran (cubierto por test).

## Postman
Colección y entorno importables en [postman/](postman/). Definir `apiKey` en el entorno (`baseUrl` por defecto: `http://localhost:8080`).
