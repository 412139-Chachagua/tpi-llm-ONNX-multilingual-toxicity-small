# Runbook de despliegue en Azure Container Apps (Chile Central)

> **Ningún comando de este documento se ha ejecutado contra Azure.** Los flags son un borrador: antes de cada comando de
> creación hay que validarlos con `az <comando> --help`. Cada paso se ejecuta solo con aprobación explícita.

## Decisiones
- **Suscripción:** Azure for Students (Id `ee1f9108-02b9-473f-8a37-103777654bdc`, estado *Enabled*, rol Propietario).
- **Región: `chilecentral`.** La política *Allowed resource deployment regions* solo permite `southafricanorth`, `chilecentral`,
  `northcentralus`, `canadacentral` y `mexicocentral`. East US y Brazil South están **excluidas**. Chile Central es la más cercana
  a Argentina (latencia no medida) y ~40 % más cara en cómputo que North Central US (precios oficiales de la Retail Prices API).
- **Modelo dentro de la imagen** (`Dockerfile.azure`): descarga con revisión fija y SHA-256 verificado en el build. Probada en local sin volumen.
- **Plan Consumption**, 1 vCPU / 2 GiB, mínimo 0 y máximo 1 réplica, ingress HTTPS externo + API key como secreto.
- El grupo `llm` (East US) que ya existe **no se reutiliza ni se toca**.

## Costos estimados (USD/mes, 1 vCPU / 2 GiB, Chile Central; cálculo propio con precios oficiales)
| Escenario | Cómputo | ACR Basic | Total |
|---|---|---|---|
| Pruebas con escala a cero (~60 h activas) | ~1,5 | ~5 | **~6,5** |
| 1 réplica siempre encendida, casi en reposo | ~29 | ~5 | **~34** |
| 1 réplica siempre encendida y activa | ~96 | ~5 | **~101** (supera el crédito) |
Sin verificar: Log Analytics, transferencia de datos, tiempo de build en ACR. Las alertas de presupuesto avisan, no detienen cargos;
en Azure for Students, al agotarse el crédito la suscripción se deshabilita (según la documentación de Microsoft).

## Pasos
Variables sugeridas (el nombre del registro debe ser único globalmente, solo minúsculas y números):
```bash
LOC=chilecentral; RG=rg-toxicity-chilecentral; ACR=<nombre-unico>; ENV=cae-toxicity; APP=toxicity-service; VER=1.0.0
```

### A. Consulta (sin costo)
| Comando | Qué hace |
|---|---|
| `az account show -o table` | Confirma suscripción activa |
| `az provider show -n Microsoft.App --query registrationState -o tsv` (y `Microsoft.ContainerRegistry`, `Microsoft.OperationalInsights`) | Estado de proveedores |
| `az group list -o table`, `az acr list -o table`, `az containerapp list -o table` | Recursos existentes |

### B. Crean o modifican (requieren aprobación)
| # | Comando | Efecto | ¿Costo? | Verificación |
|---|---|---|---|---|
| 1 | `az provider register -n Microsoft.App --wait` (idem `Microsoft.ContainerRegistry`, `Microsoft.OperationalInsights`) | Registra proveedores en la suscripción | No | `registrationState` = Registered |
| 2 | `az group create -n $RG -l $LOC` | Crea el grupo de recursos | No | `az group show -n $RG` |
| 3 | `az acr create -n $ACR -g $RG -l $LOC --sku Basic` | Crea el registro (admin deshabilitado) | **Sí**, ~0,17 USD/día | `az acr show -n $ACR` |
| 4 | Publicar la imagen (ver abajo) | Sube `toxicity-service:$VER` | Almacenamiento (10 GB incluidos en Basic) | `az acr repository show-tags -n $ACR --repository toxicity-service` |
| 5 | `az containerapp env create -n $ENV -g $RG -l $LOC` | Crea el entorno (con Log Analytics por defecto) | Posible (logs; sin verificar) | `az containerapp env show -n $ENV -g $RG` |
| 6 | `az containerapp create -n $APP -g $RG --environment $ENV --image $ACR.azurecr.io/toxicity-service:$VER --registry-server $ACR.azurecr.io --registry-identity system --target-port 8080 --ingress external --cpu 1.0 --memory 2.0Gi --min-replicas 0 --max-replicas 1 --secrets api-key=<VALOR> --env-vars TOXICITY_API_KEY=secretref:api-key` | Crea la app con la API key como secreto | **Sí** (consumo) | `az containerapp show -n $APP -g $RG --query properties.provisioningState` |
| 7 | `az containerapp update -n $APP -g $RG --yaml deploy/azure/probes.yaml` | Aplica sondas (reemplazar `<ACR>` y `<VERSION>`) | No | `az containerapp show ... --query properties.template.containers[0].probes` |

**Secreto:** generar la clave en el propio Cloud Shell (`openssl rand -base64 32`), guardarla en un gestor de contraseñas y **no pegarla
en chats ni en el repo**.

**Publicar la imagen (paso 4)** — dos caminos:
1. `az acr build -r $ACR -t toxicity-service:$VER -f Dockerfile.azure .` (compila en Azure). **Puede estar bloqueado en suscripciones de
   estudiante** (ACR Tasks); no verificado.
2. Alternativa: `docker build -f Dockerfile.azure -t $ACR.azurecr.io/toxicity-service:$VER .` en el equipo local (linux/amd64), iniciar
   sesión en el registro y `docker push`. Requiere credenciales del registro (p. ej. habilitar temporalmente el usuario admin y
   deshabilitarlo después). Documentar y revertir cualquier cambio.

### C. Pruebas desde una máquina externa
```bash
URL=https://$(az containerapp show -n $APP -g $RG --query properties.configuration.ingress.fqdn -o tsv)
curl -s $URL/health/ready                       # 200 {"status":"UP"} (la primera vez puede tardar: arranque desde cero)
curl -s -o /dev/null -w '%{http_code}\n' -X POST $URL/api/v1/toxicity/classify -H 'Content-Type: application/json' -d '{"text":"x"}'   # 401
curl -s -X POST $URL/api/v1/toxicity/classify -H 'Content-Type: application/json' -H "X-API-Key: <CLAVE>" \
  -d '{"text":"You are a complete idiot and nobody wants you here."}'
```
Criterio de validez de la inferencia: `toxicity` ≈ 0,924 e `insult` ≈ 0,920 para ese texto (valores medidos en local con el mismo modelo
y la misma imagen; ver `reference-scores.json`), siete categorías en [0,1]; **no basta un 200**. Probar también texto vacío (400),
texto de 5001 caracteres (413) y JSON inválido (400), sin trazas en las respuestas.

### D. Observación
`az containerapp logs show -n $APP -g $RG --follow`, `az containerapp replica list -n $APP -g $RG -o table` (reinicios) y Cost Management
en el portal. Revisar memoria (esperado ~1,3 GiB de 2 GiB) y el consumo tras las primeras pruebas.

### E. Reducir, detener o eliminar
- Reducir: `az containerapp update -n $APP -g $RG --min-replicas 0 --max-replicas 1`.
- Eliminar todo (irreversible): `az group delete -n $RG --yes --no-wait`.
