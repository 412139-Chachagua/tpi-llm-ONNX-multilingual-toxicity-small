# toxicity-inference-service

Mini servicio independiente de inferencia de toxicidad: ejecuta el modelo
`Horizon-Labs/multilingual-toxicity-small` con ONNX Runtime desde Java/Spring Boot.
No decide bloqueos ni se integra con el TPI principal; solo devuelve puntuaciones.

> Estado: Fases 1–7 (proyecto, modelo, inferencia real, API REST, seguridad, pruebas, Docker y preparación de despliegue). Contrato en [docs/api.md](docs/api.md).

## Modelo
`scripts/download-model.sh` descarga y verifica los artefactos en `models/` (detalles en [docs/model.md](docs/model.md)).

## Requisitos
- JDK 21
- Maven 3.9+
- Docker (a partir de la Fase 6)

## Comandos
```bash
mvn verify                      # compila y ejecuta los tests
TOXICITY_API_KEY=cambia-esto mvn spring-boot:run   # puerto 8080 (requiere los artefactos de models/; ver sección Modelo)
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
| `TOXICITY_API_KEY` | — (obligatoria) | API key (cabecera `X-API-Key`); nunca se versiona. Sin ella la app no arranca |
| `TOXICITY_AUTH_DISABLED` | `false` | `true` solo en desarrollo local para omitir la API key |

## Estructura
`api` (controladores), `service` (inferencia), `model` (configuración y carga del modelo), `dto`.

## Tests
- Unitarios y de API (sin modelo): `mvn verify`.
- Con modelo real (arranque completo, línea base de regresión, concurrencia): ver sección anterior. La línea base
  `src/test/resources/reference-scores.json` es una **línea de regresión generada con este mismo modelo**, no una referencia
  independiente (p. ej. PyTorch); se regenera con `-Dupdate.reference=true`.

## Docker
Ver [docs/docker.md](docs/docker.md) (construcción, ejecución, mediciones y fallos verificados).

## Despliegue
Preparación y pendientes para Azure Container Apps (sin desplegar): [docs/deployment.md](docs/deployment.md).
