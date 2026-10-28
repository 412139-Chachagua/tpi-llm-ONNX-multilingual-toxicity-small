# Preparación para despliegue en Azure Container Apps

> **Actualización:** región elegida `chilecentral` (única permitida cercana por la política de la suscripción), modelo incluido en la imagen
> con `Dockerfile.azure`; los pasos concretos están en [azure-runbook.md](azure-runbook.md).
>
> **Nada de esto se ha ejecutado.** No se crearon recursos, no se publicó ninguna imagen y no se estimaron costos.
> Este documento reúne las decisiones y los pasos pendientes (Fase 7 del plan) a partir de lo medido localmente.

## 1. Lo que ya está listo
- Imagen reproducible (`Dockerfile`), no-root, sin secretos, puerto 8080 (`PORT`), configuración 100 % por variables de entorno.
- Sondas disponibles: `GET /health/live` (proceso vivo) y `GET /health/ready` (modelo cargado).
- Autenticación por API key en `/api/**`; el servicio no arranca sin `TOXICITY_API_KEY`.
- Fallo explícito de arranque si falta/está corrupto el modelo (la réplica no llega a ser *ready*).

## 2. Dimensionamiento (punto de partida, a validar en Azure)
Mediciones locales de `docs/docker.md` (linux/amd64, una ejecución): ~1,2 GiB de memoria en reposo, arranque ~15 s,
latencia mediana ~150 ms para ~280 caracteres.
- Memoria: **partir de 2 GiB** por réplica (1 GiB arrancó en reposo pero no se probó bajo carga).
- CPU: **no determinada**. Las pruebas usaron 16 CPUs visibles sin límite; hay que medir latencia y throughput con
  p. ej. 1 y 2 vCPU y ajustar `TOXICITY_MAX_CONCURRENT` (inferencias simultáneas) en consecuencia antes de fijar el valor.
- Los límites de caracteres/concurrencia/timeout siguen siendo los provisionales del `application.yml`.

## 3. Configuración
| Variable | Origen en Azure |
|---|---|
| `TOXICITY_API_KEY` | **Secreto** de Container Apps (idealmente referenciado a Key Vault); nunca en la imagen ni en el repositorio |
| `TOXICITY_MODEL_PATH`, `TOXICITY_TOKENIZER_PATH` | variables normales (por defecto `/models/...`) |
| `TOXICITY_MAX_CHARS`, `TOXICITY_MAX_CONCURRENT`, `TOXICITY_INFERENCE_TIMEOUT`, `TOXICITY_MAX_TOKENS` | variables normales, según §2 |
| `JAVA_OPTS` | por defecto `-XX:MaxRAMPercentage=70`; revisar al fijar la memoria |
| `TOXICITY_AUTH_DISABLED` | **no definir** (solo desarrollo local) |

## 4. Red y acceso
- Ingress HTTPS habilitado (Container Apps termina TLS). Preferible ingress **interno** (solo accesible desde el entorno/VNet) si
  los clientes futuros viven en el mismo entorno; si se expone externamente, la API key es la única barrera: usar una clave larga
  aleatoria, rotarla y valorar restricciones por IP.
- No hay rate limiting por cliente en el servicio (solo concurrencia global y 503 al saturarse).

## 5. Sondas y arranque
La carga del modelo tarda ~15 s (puede ser mayor con almacenamiento remoto). Configurar:
- *Startup probe* sobre `/health/ready` con margen amplio (p. ej. fallos tolerados × período ≥ 2–3 min).
- *Readiness probe* `/health/ready` y *liveness probe* `/health/live`. La liveness no debe depender del modelo.

## 6. Entrega del modelo — decisión pendiente
La imagen no contiene el modelo (563 MB). Opciones:
1. **Volumen de Azure Files montado en `/models`** (la imagen actual ya lo soporta). Permite actualizar el modelo sin reconstruir; el
   arranque depende del rendimiento del almacenamiento y hay que verificar el tiempo de carga real.
2. **Imagen que incluya el modelo** (variante del Dockerfile con una etapa que copie `models/` verificando los SHA-256 de `docs/model.md`).
   Despliegue autocontenido y versión del modelo = versión de la imagen, a costa de una imagen de ~1,3 GB y del registro.
Ninguna está implementada más allá del montaje por volumen; hay que elegir y, si se elige 2, añadir ese Dockerfile.
Cualquiera que sea la opción, el modelo se identifica por la revisión fijada `01adcc628a33848569816d4851a38544c321b92c`.

## 7. Escalado
- Cada réplica carga su propia copia del modelo (~1,2 GiB). Mínimo de réplicas ≥ 1 para evitar arranques en frío de ~15 s en las
  peticiones; máximo acotado por presupuesto. Regla de escalado por concurrencia de HTTP, a definir tras medir throughput por réplica.

## 8. Observabilidad — pendiente
El servicio no expone métricas todavía (sin Actuator/Micrometer, por no ampliar dependencias). Mínimo deseable: contadores de
peticiones/errores por código, latencia y estado de disponibilidad, **sin registrar texto del usuario**. Hasta decidirlo, se dispone de
los logs del contenedor (que no contienen texto, claves ni cabeceras) y de las métricas de plataforma de Container Apps.

## 9. Actualizar
- Imagen: reconstruir, etiquetar con versión inmutable (no `latest`), publicar en el registro, crear nueva revisión.
- Modelo: cambiar la revisión fijada, actualizar `docs/model.md` (revisión, SHA-256, licencia), regenerar la línea base de
  `reference-scores.json` y validar con los tests de modelo real antes de desplegar.

## 10. Pasos pendientes antes de desplegar (requieren aprobación explícita)
1. Aprobación para crear recursos y estimación de costos (registro, entorno de Container Apps, almacenamiento, logs).
2. Elegir entrega del modelo (§6) y medir CPU/memoria/latencia reales en Azure (§2).
3. Definir ingress interno vs externo y gestión/rotación de la API key.
4. Decidir métricas (§8) y límites definitivos de caracteres/concurrencia/timeout.
5. Probar linux/arm64 si se pretende usar arquitecturas ARM (no verificado).
6. Verificar el tokenizer contra una referencia independiente (hoy solo se comprobó el comportamiento: BOS/EOS y truncamiento).
7. Cuando exista el entorno: probar `/health/*` y una clasificación real por HTTPS desde un cliente autorizado.
