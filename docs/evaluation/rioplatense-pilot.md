# Piloto: textos rioplatenses en ambos modelos

**Alcance:** 60 textos (30 tóxicos, 30 no tóxicos) en `rioplatense-pilot.csv`. **Textos y etiquetas escritos por el equipo de
desarrollo como piloto, no por anotadores independientes**: sirven para orientar, no para fijar umbrales definitivos. Reemplazar o
ampliar con un set real etiquetado (200–500 textos) antes de decidir.

Grupos: `insulto_lunfardo`, `amenaza`, `discriminacion`, `insulto_general` (tóxicos); `uso_amistoso`, `neutral_voseo`,
`critica_no_toxica` (no tóxicos).

## Cómo correrlo
Con el servicio levantado (ambos modelos):
```bash
postman collection run docs/postman/rioplatense-eval.postman_collection.json -d docs/evaluation/rioplatense-pilot.csv \
  --env-var baseUrl=http://localhost:18080 --env-var apiKey=<API_KEY> > run.log
python3 scripts/eval-rioplatense.py docs/evaluation/rioplatense-pilot.csv run.log
```
Small = máximo de sus 7 categorías; español = `scores.TOXIC`.

## Resultado de la corrida del 2026-10-30 (local, revisión `fe9d55ff…` del modelo español)
TP = tóxicos detectados (de 30); FP = no tóxicos marcados (de 30).

| Umbral | Small | Español | OR | AND |
|---|---|---|---|---|
| 0,4 | 26 / 4 | 29 / 9 | 29 / 11 | 26 / 2 |
| 0,5 | 21 / 1 | 29 / 7 | 29 / 7 | 21 / 1 |
| 0,7 | 8 / 0 | 29 / 7 | 29 / 7 | 8 / 0 |
| 0,8 | 2 / 0 | 29 / 6 | 29 / 6 | 2 / 0 |

Con umbrales distintos por modelo, `small ≥ 0,4 Y español ≥ 0,8` dio 26 TP / 2 FP y `small ≥ 0,25 Y español ≥ 0,8`, 27 / 3.

## Observaciones (sobre esta muestra)
- El español detecta casi todo el lunfardo tóxico (29/30 entre 0,5 y 0,8); el small deja pasar varios (p. ej. "Qué forro que sos" 0,14).
- Los falsos positivos del español son usos amistosos ("Che boludo ¿venís esta noche?" 0,74; "Qué hacés boludo tanto tiempo" 0,91);
  el small los puntúa bajo en general.
- OR equivale al español solo; AND usa al small como filtro de contexto y reduce falsos positivos.
- Ningún modelo detectó la amenaza implícita "Cuidate porque sé dónde vivís".
