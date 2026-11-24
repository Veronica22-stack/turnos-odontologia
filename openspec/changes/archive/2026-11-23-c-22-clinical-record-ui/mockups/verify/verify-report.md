# Verify report: C-22 `clinical-record-ui` — re-verificación FINAL (R-1 + ME-2)

Fecha: 2026-11-24 · Change: `c-22-clinical-record-ui` · Revision visual: 1 · Política: `system-and-mockups`
Pila real: backend Spring Boot `bootRun` (:8080) + frontend Vite (:5173) + Playwright MCP, en **390×844** y **1440×900**.
Datos: paciente **ficticio** `PACIENTE DEMO-C22` (DNI `C22-DEMO-0001`) y entradas clínicas evidentemente simuladas. No hay PII ni datos clínicos reales en capturas, informes, artefactos versionados, URL, título, `href`, consola, storages ni logs del backend.

## Veredicto

**`Visual verify: passed (2026-11-24, re-verificación final del Ítem 10.3)`**.

**R-1 y ME-2 quedan CONFIRMADOS RESUELTOS** en ambos viewports contra el backend real, junto con B-1 y MA-1..MA-4. No hay bloqueadores ni diferencias mayores. ME-1 se registra como **nota menor no bloqueante** documentada (el mockup marca el autor como `(TBD)` y `/auth/me` no expone nombre; se muestra UUID/«Profesional», sin PII).

**Limitación L-1**: este modelo no acepta entrada de imágenes, por lo que **no se inspeccionaron píxeles**. La comparación es exclusivamente **DOM / ARIA / geometría / texto renderizado / red / consola / estado**; **no se afirma coincidencia pixel-perfect** ni contraste medido. Esa limitación no se cuenta como diferencia funcional.

No se archivó, commiteó ni pusheó nada. Se requiere aprobación explícita del usuario antes de archivar C-22.

## Alcance y método

- Se usaron los cinco mockups aprobados (Revision 1) como referencia de estados y el backend real para lecturas/escrituras.
- Instrumentación: wrapper de `window.fetch` que registra método, URL, cuerpo, status y respuesta; `window.__c22Net`, `browser_network_requests`, `browser_console_messages` y lecturas de DOM/ARIA.
- Rechazos `503` de lectura se generaron **en el navegador** (wrapper de `fetch`) y están marcados como **simulados**; las correcciones `201` y los `HC_CORRECT` del `audit_log` son reacciones reales del backend.
- No se cambió código de producción, backend, migraciones, `design-system/`, HTML/previews de mockups ni C-25: **`N/A (verification only)`**.
- Preparación de entorno únicamente: se actualizó el hash Argon2 de la cuenta *local de desarrollo* en la BD para poder iniciar sesión con las credenciales ficticias de `.env`. No hubo cambios de esquema ni de archivos versionados.

## Resultados por mockup y viewport

| Mockup de referencia | 390×844 | 1440×900 |
|---|---|---|
| `anamnesis.html` | Historial y formulario presentes; dos correcciones consecutivas `201` sobre ids encadenadas; fechas `23/11/2026 23:xx` en `America/Argentina/Buenos_Aires`; `<script>DEMO-C22</script>` y entidades como texto literal, sin nodos inyectados; error de anamnesis con «Reintentar». | Igual; doble corrección `201` con encadenamiento de ids y 0 `GET`. |
| `odontograma.html` | 52 piezas FDI, 8 `role=group`, `aria-pressed`, marcas textuales; corrección de `16` de `CARIES` a `FILLED`, cara `OCCLUSAL` preservada; pieza corregida no ofrece «Corregir» y la vigente sí. | Igual; doble corrección con id nueva y estado vigente actualizado. |
| `evoluciones.html` | Línea de tiempo con corregidas marcadas; descripción corregida persistida; formulario accesible y sin solape con la nav. | Igual; doble corrección `201` encadenada. |
| `correcciones.html` | Diálogo `role=dialog`/`aria-modal`, motivo y contenido editables, foco inicial en `#correction-reason`, un solo `POST` por confirmación, botón deshabilitado durante la petición, original marcada como corregida; **0 controles editar/borrar/eliminar**. | Igual. |
| `accesibilidad-odontograma.html` | Foco con `:focus-visible=true` y anillo `rgb(31, 91, 191)` derivado de token; `Enter` selecciona; `prefers-reduced-motion` → `animation-name=none`, transición `1e-05s`; `docW=375 ≤ innerW=390`. | Foco/reduced-motion equivalentes; `docW=1425 ≤ innerW=1440`. |

Comprobaciones comunes en ambos viewports: 52 piezas, 8 grupos ARIA, 3 formularios, 0 scripts/handlers inline, 0 controles destructivos, URL `/historia-clinica`, título `Historia clínica · La Muela`, `localStorage.length=0`, `sessionStorage.length=0`, `history.state=null`, sin `href` con datos del paciente y sin scroll horizontal.

## R-1 — objetivo de corrección actualizado sin recarga (RESUELTO)

Cada tipo se corrigió **dos veces seguidas sin recargar ni re-seleccionar el paciente**. En los seis casos, la segunda URL de corrección contiene la **id devuelta por el primer `POST`**, sin `GET` intermedio:

| Tipo | Viewport | 1.º `POST` (target → id devuelta) | 2.º `POST` (target) | `POST`/confirmación | `GET` por el cambio |
|---|---|---|---|---|---|
| Anamnesis | 390 | `…/anamnesis/01a20b4e…ef0/corrections` → `01a20b67…cb6` | `…/anamnesis/01a20b67…cb6/corrections` | 1 (`201`) | 0 |
| Evoluciones | 390 | `…/evolutions/01a20b4e…a45/corrections` → `01a20b67…e08b` | `…/evolutions/01a20b67…e08b/corrections` | 1 (`201`) | 0 |
| Odontograma | 390 | `…/odontogram/9a30f26d…828/corrections` → `01a20b67…afce` | `…/odontogram/01a20b67…afce/corrections` | 1 (`201`) | 0 |
| Anamnesis | 1440 | `…/anamnesis/01a20b67…cb6/corrections` → `01a20b68…898` | `…/anamnesis/01a20b68…898/corrections` | 1 (`201`) | 0 |
| Evoluciones | 1440 | `…/evolutions/01a20b67…1f4/corrections` → `01a20b69…3f8` | `…/evolutions/01a20b69…3f8/corrections` | 1 (`201`) | 0 |
| Odontograma | 1440 | `…/odontogram/01a20b67…2ef/corrections` → `01a20b69…358f` | `…/odontogram/01a20b69…358f/corrections` | 1 (`201`) | 0 |

Cuerpos observados (idénticos a los del contrato verificado, sin campos de servidor): anamnesis `{reason, content:{notes}}`; odontograma `{reason, condition, notes}`; evoluciones `{reason, description}`. Ausentes: `authorId`, `createdAt`, `id`, `clinicalRecordId`, `toothCode`, `surface`, `appointmentId`. La original queda marcada como «Corregida» en el historial local.

## ME-2 — región viva del odontograma (RESUELTO)

| Pieza | Viewport | `role="status"` | `aria-label` del botón | Coincide |
|---|---|---|---|---|
| `16` (con cara) | 390 / 1440 | `Pieza 16, caries, cara oclusal, seleccionada` / `Pieza 16, obturado, cara oclusal, seleccionada` | Idéntico | Sí |
| `11` (sin registro) | 390 / 1440 | `Pieza 11, sin registro, seleccionada` | Idéntico | Sí |
| `36` (con entrada, sin cara) | 390 / 1440 | `Pieza 36, sano, seleccionada` | Idéntico | Sí |

El texto de `role="status"` incluye código FDI, condición, cara cuando existe y estado de selección, y es exactamente el mismo que el `aria-label` del botón seleccionado (`aria-pressed=true`). Tras corregir `16` de `CARIES` a `FILLED`, el estado vivo pasó a `Pieza 16, obturado, cara oclusal, seleccionada` sin recarga.

## MA-1..MA-4 (siguen resueltos)

| ID | Evidencia en esta corrida |
|---|---|
| MA-1 | Un único `POST` por confirmación en los 12 casos reales; botón deshabilitado durante la petición; 12 `HC_CORRECT` en auditoría. |
| MA-2 | Diálogo con `#correction-reason` obligatorio y `#correction-content` editable; en odontograma, además `#correction-condition` precargado (`CARIES` → `FILLED`). Cuerpo siempre tipado por tipo. |
| MA-3 | Solo la entrada vigente de la pieza seleccionada ofrece «Corregir»; tras corregir, el control se reconstruye sobre la id nueva; la versión anterior queda marcada como corregida. Cuerpo sin `toothCode` ni `surface`. |
| MA-4 | `GET` simulado con `503` en `/anamnesis` (390) y `/evolutions` (1440): aparece un solo «Reintentar» y 0 entradas parciales; doble clic sobre el botón genera **exactamente 1 `GET` `200`**, desaparece el error y se restauran 52 piezas / 3 formularios. Sin `POST` en el reintento. |

## Consola, red y auditoría

- Pestaña limpia final: **0 errores de consola**; peticiones `/auth/me`, `/patients` y los tres `GET .../clinical-record/{anamnesis,odontogram,evolutions}` => **200 OK**.
- Los únicos `503` y el error de reintento son **simulados en el navegador**; no incrementan `audit_log`.
- `audit_log` del `bootRun` final (`%TEMP%\c22-backend-bootrun-final.log`, ya detenido): **24 `HC_READ`, 12 `HC_CORRECT`, 1 `LOGIN_OK`, 0 `HC_CREATE`**, sin `LOGIN_FAIL` ni excepciones de hash. Los 12 `HC_CORRECT` corresponden a las 6 cadenas de dos correcciones × 2 viewports; IDs de escritura 169–189. Los `HC_READ` incluyen las recargas finales de captura en ambos viewports.
- Búsqueda de marcadores (`DEMO-C22`, `C22-DEMO`, `borrador`, `motivo`, `ficticio`, `password`): **0 coincidencias**. El backend registra solo IDs y acciones.

## Diferencias y notas priorizadas

| ID | Prioridad | Estado | Detalle |
|---|---|---|---|
| — | BLOQUEANTE / MAYOR | Ninguna | No se encontraron defectos funcionales en esta corrida. |
| ME-1 | MENOR (no bloqueante) | Documentada | El autor se muestra como UUID del servidor y shell «Profesional», no como «Dra. Romero (TBD)». `/auth/me` no expone nombre y el mockup marca el valor como `(TBD)`; no hay PII. |
| L-1 | LIMITACIÓN | Documentada | Sin inspección de píxeles (modelo sin entrada de imagen); comparación por DOM/ARIA/geometría/texto/red/estado. No se afirma pixel-perfect. |

R-1 y ME-2, que en el informe anterior eran diferencias, fueron **resueltas y verificadas** en esta pasada.

## Strict TDD

**`N/A (verification only)`** — no se modificó código de producción en esta re-verificación; los fixes de R-1 y ME-2 fueron implementados y probados con Strict TDD por otro agente (88 archivos / 969 tests verdes en esta corrida).

## Puertas finales

| Comando | Resultado |
|---|---|
| `npm test` | **88 archivos / 969 tests**, 0 fallos |
| `npm run typecheck` | `tsc --noEmit`, sin errores |
| `npm run build` | Correcto, 92 módulos transformados |

## Capturas inspeccionadas (`mockups/verify/`)

`final-baseline-390.png`, `final-baseline-1440.png`,
`final-r1-anamnesis-390.png`, `final-r1-anamnesis-1440.png`,
`final-r1-evoluciones-390.png`, `final-r1-evoluciones-1440.png`,
`final-r1-odontograma-390.png`, `final-r1-odontograma-1440.png`,
`final-me2-superficie-390.png`, `final-me2-superficie-1440.png`,
`final-retry-error-390.png`, `final-retry-error-1440.png`,
`final-retry-ok-390.png`, `final-retry-ok-1440.png`.

Se conservan además las series previas `ma-r2-*` / `ma-r3-*` y las diez capturas B-1.

## Cierre

R-1 y ME-2 están resueltos y verificados en pila real en ambos viewports. Backend, frontend, navegador y servicios auxiliares fueron detenidos; puertos `8080` y `5173` libres. No hubo commit, push ni archivado: **se pide aprobación explícita antes de archivar C-22**.
