# toxicity-inference-service

Mini servicio independiente de inferencia de toxicidad: ejecuta el modelo
`Horizon-Labs/multilingual-toxicity-small` con ONNX Runtime desde Java/Spring Boot.
No decide bloqueos ni se integra con el TPI principal; solo devuelve puntuaciones.

> Estado: **Fase 1** (proyecto base). Aún no hay modelo ni endpoints.

## Requisitos
- JDK 21
- Maven 3.9+
- Docker (a partir de la Fase 6)

## Comandos
```bash
mvn verify                      # compila y ejecuta los tests
mvn spring-boot:run             # arranca en el puerto 8080
```

### Tests con el modelo real
Requieren los artefactos en `models/` (ver [docs/model.md](docs/model.md)); si faltan, esos tests se omiten.
DJL tokenizers no trae nativos para macOS Intel, así que en ese equipo se ejecutan en un contenedor Linux:
```bash
docker run --rm -v "$PWD":/app -v toxicity-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-21 mvn -B verify
```

## Configuración (variables de entorno)
| Variable | Por defecto | Descripción |
|---|---|---|
| `PORT` | `8080` | Puerto HTTP |
| `TOXICITY_MODEL_PATH` | `models/onnx/model.onnx` | Ruta al modelo ONNX |
| `TOXICITY_TOKENIZER_PATH` | `models/tokenizer.json` | Ruta al tokenizer |
| `TOXICITY_MAX_TOKENS` | `256` | Truncamiento en tokens (el modelo se entrenó con 256) |
| `TOXICITY_MAX_CHARS` | `5000` | Máximo de caracteres (provisional) |
| `TOXICITY_MAX_CONCURRENT` | `4` | Inferencias simultáneas (provisional) |
| `TOXICITY_INFERENCE_TIMEOUT` | `10s` | Timeout de inferencia (provisional) |
| `TOXICITY_API_KEY` | vacío | API key del endpoint de clasificación; nunca se versiona |

## Estructura
`api` (controladores), `service` (inferencia), `model` (configuración y carga del modelo), `dto`.
