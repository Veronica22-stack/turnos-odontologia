# Mockups — C-21 `patients-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Decision: yes (2026-11-23)
Mockup revision: 1
Preview: rendered (2026-11-23, `previews/<estado>-390.png` y `previews/<estado>-1440.png`)
Visual verify: passed (2026-11-23; 40 capturas en `verify/` a 390×844 y 1440×900; `reviewRequired` simulado en navegador porque C-10 no expone el campo; ver `../tasks.md` §«Resumen del apply — verificación manual (10.3)»)
Approval: approved (2026-11-23, by user)

Un HTML por estado, directamente openable; sin frameworks, fuentes, CDNs ni assets remotos. Cada archivo importa `../../../../design-system/tokens.css` y `../../../../design-system/brand.js` y usa el logo de `design-system/assets/logo.svg` (rotulado **PROVISIONAL**, `(TBD)`). La navegación «Estados:» al pie de cada mockup es solo ayuda de revisión: no existe en la aplicación. El resumen de diseño está en [design-brief.md](design-brief.md).

## Pantallas y estados

Ruta representada: `/pacientes` (privada, con «Pacientes» marcado como actual en el shell de C-05; mismo header, navegación y avatar que C-19).

| Archivo | Estado | Qué muestra | Capturas |
|---------|--------|-------------|----------|
| [list.html](list.html) | Listado con resultados | `GET /pacientes` con 5 pacientes ficticios; buscador por nombre, DNI o teléfono; tabla desktop y lista apilada móvil. | `previews/list-390.png`, `previews/list-1440.png` |
| [no-results.html](no-results.html) | Búsqueda sin coincidencias | 200 con arreglo vacío y término de búsqueda: «No encontramos pacientes con ese criterio», sugiere DNI/teléfono. | `previews/no-results-390.png`, `previews/no-results-1440.png` |
| [no-patients.html](no-patients.html) | Sin pacientes | 200 vacío sin término: «Todavía no hay pacientes», distinto del caso anterior; sin botón «Crear paciente». | `previews/no-patients-390.png`, `previews/no-patients-1440.png` |
| [loading.html](loading.html) | Cargando | `role="status"` con spinner y esqueleto; sin filas ni resultados falsos. | `previews/loading-390.png`, `previews/loading-1440.png` |
| [connection-error.html](connection-error.html) | Error de conexión | Red/timeout/5xx/ilegible al cargar: aviso propio + «Reintentar». | `previews/connection-error-390.png`, `previews/connection-error-1440.png` |
| [detail.html](detail.html) | Ficha de paciente | Nombre, DNI (solo lectura), teléfono, email, nacimiento, dirección, origen y fechas en Buenos Aires; insignia «Activo»; «Editar ficha» y «Dar de baja». | `previews/detail-390.png`, `previews/detail-1440.png` |
| [detail-review.html](detail-review.html) | Ficha con «Datos para revisar» | RN-PA-03 con `reviewRequired: true`: insignia con ícono + texto y explicación de no sobreescritura; origen `PUBLIC_BOOKING`. | `previews/detail-review-390.png`, `previews/detail-review-1440.png` |
| [not-found.html](not-found.html) | No encontrado | 404 `PATIENT_NOT_FOUND`: «No encontramos este paciente» + «Volver al listado», sin id en el mensaje. | `previews/not-found-390.png`, `previews/not-found-1440.png` |
| [edit.html](edit.html) | Edición | Formulario precargado; DNI y origen como texto de solo lectura; «Guardar cambios» y «Cancelar». | `previews/edit-390.png`, `previews/edit-1440.png` |
| [edit-field-error.html](edit-field-error.html) | Error por campo | Borde de 2px + ícono + texto junto a nombre y teléfono; aviso general; cambios conservados. | `previews/edit-field-error-390.png`, `previews/edit-field-error-1440.png` |
| [stale-page.html](stale-page.html) | Página desactualizada | 403 `CSRF_REQUIRED`: «La página quedó desactualizada… No se hizo ningún cambio», sin afirmar guardado. | `previews/stale-page-390.png`, `previews/stale-page-1440.png` |
| [deactivate-confirm.html](deactivate-confirm.html) | Confirmación de baja | Diálogo «¿Dar de baja a este paciente?» con foco inicial en «No, mantener activo»; texto de conservación de turnos/HC. | `previews/deactivate-confirm-390.png`, `previews/deactivate-confirm-1440.png` |
| [deactivated.html](deactivated.html) | Baja correcta | 204: aviso info, ficha inactiva, «Volver al listado»; sin borrado físico. | `previews/deactivated-390.png`, `previews/deactivated-1440.png` |
| [deactivate-error.html](deactivate-error.html) | Error durante la baja | Red/timeout/404/403: aviso que no afirma ni niega la baja + «Reintentar» / «Ver ficha actual». | `previews/deactivate-error-390.png`, `previews/deactivate-error-1440.png` |

Archivos de soporte:

| Archivo | Contenido |
|---------|-----------|
| `design-brief.md` | Brief de diseño (público, tarea, estados, responsive, tokens, restricciones, fuentes). |
| `patients.css` | Reglas del mockup: shell privado de C-05/C-19, botones, campos, alertas, badges, tabla/lista, ficha, diálogo y estados. Solo `var(--…)` de `tokens.css`; medidas locales `--layout-*` y `--patients-*`. |
| `previews/<estado>-390.png` | Captura renderizada a 390×844, escala CSS 1, página completa salvo `deactivate-confirm` (solo viewport, para que el fondo atenuado cubra la pantalla). |
| `previews/<estado>-1440.png` | Captura renderizada a 1440×900, escala CSS 1, mismo criterio de página completa. |

## Cómo verlo

`file://` puede bloquear `tokens.css` en el entorno de render; servir la raíz del repo con un servidor estático local:

```bash
# desde la raíz del repo (cualquiera de los dos)
npx serve -l 8765
python -m http.server 8765 --bind 127.0.0.1
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-21-patients-ui/mockups/list.html
#   http://127.0.0.1:8765/openspec/changes/c-21-patients-ui/mockups/detail-review.html
#   http://127.0.0.1:8765/openspec/changes/c-21-patients-ui/mockups/deactivate-confirm.html
```

En un navegador normal también se pueden abrir los HTML con doble clic; las rutas relativas a `design-system/` funcionan con `file://` fuera de este harness. Probar a **390×844** y **1440×900** (DevTools → modo dispositivo). Detener el servidor con Ctrl+C.

Las capturas se generaron con Playwright MCP: navegación directa a cada HTML, viewport de 390×844 y 1440×900 y captura con escala CSS en `previews/`. La revisión automatizada comprobó los 28 casos: HTTP 200, título con marca enlazada, tokens y logo cargados, textos y elementos esperados visibles, visibilidad correcta de tabla/lista por viewport, sin scroll horizontal ni recursos remotos y sin errores de consola. Los defectos detectados (desborde del badge de origen en `detail-review` a 390 px y un `</div>` sobrante en `stale-page`) se corrigieron y se volvieron a renderizar los 28 casos. El modelo que generó los mockups no puede ver píxeles: la inspección visual final de las capturas queda pendiente del usuario.

## Fuentes

- `design-system/README.md`, `tokens.css`, `brand.js`, `components.html` y `assets/logo.svg` — Revision 1, aprobada.
- `openspec/changes/archive/2026-11-23-c-19-settings-ui/mockups/` — shell privado, botones, campos, alertas y diálogo de confirmación.
- `openspec/changes/c-25-booking-management-ui/mockups/` y C-24 — patrón de navegación entre estados y notas de revisión.
- `knowledge-base/06_funcionalidades.md` §US-050; `knowledge-base/05_reglas_de_negocio.md` §RN-PA-01..04 (especialmente RN-PA-03 y RN-PA-04).
- Contrato del backend C-10 leído desde el código y documentado en `design.md` D2.
- Especificaciones del change: `specs/patients-ui/spec.md`, `specs/spa-routing/spec.md`; `design.md` D1–D9 y §UI Mockups; `proposal.md`; `tasks.md` grupos 4–7.

## Placeholders `(TBD)`

- Logo: `design-system/assets/logo.svg` está rotulado **PROVISIONAL** y se muestra intacto.
- Nombre del profesional («Dra. Romero») y consultorio: datos de ejemplo `(TBD)`.
- Todos los pacientes son ficticios y evidentes: nombres simulados, DNI `00.xxx.xxx`, teléfonos `11 5555 000x`, dirección «Calle Falsa 123» y emails `@ejemplo.invalid`.
- El marcador `reviewRequired` de RN-PA-03 depende de un pedido P1 al backend (design D3); el mockup muestra el estado condicionado a que el servidor lo informe.

## Notas de diseño

- Mobile-first: una columna, lista apilada en lugar de tabla, acciones a ancho completo y navegación inferior; sin desplazamiento horizontal de página. Desktop (≥ 64rem): tabla con encabezados asociados y navegación lateral.
- El buscador admite nombre, apellido, DNI y teléfono; el término no se escribe en la URL, el título ni la consola (el título es fijo: «Pacientes · {{brand}} — mockup C-21»).
- Estados nunca solo por color: ícono + texto en alertas e insignias; foco visible con `--focus-ring`; movimiento solo con tokens y `prefers-reduced-motion`.
- La baja es lógica y conserva turnos e historia clínica; no hay controles de borrado físico. El diálogo representa visualmente el foco inicial en la opción segura; la implementación usa `<dialog>` nativo con foco devuelto al origen.
- Los datos del paciente se pintan como texto; en la implementación se usa `h()`/`textContent`, nunca `innerHTML`.

## Aprobación

Aprobación **aprobada (2026-11-23, by user)**, revision 1. `Approval: approved` queda registrado aquí y en `design.md` §UI Mockups. Cualquier cambio visual posterior reinicia la aprobación a `pending`.
