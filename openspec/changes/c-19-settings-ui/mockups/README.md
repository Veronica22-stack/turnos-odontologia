# Mockups — C-19 `settings-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Mockup revision: 1
Decision: yes (2026-11-22)
Approval: approved (2026-11-22, by user)
Preview: rendered (2026-11-22, `previews/<estado>-390.png` y `previews/<estado>-1440.png`)

Un único archivo HTML con selector de estado, sin frameworks, CDN, fuentes ni assets remotos. Cada estado tiene un valor `?state=` estable y una captura propia en ambos viewports. La barra superior del mockup es solo ayuda de revisión: no existe en la aplicación.

## Pantallas y estados

Ruta representada: `/configuracion` (privada, con «Configuración» marcada como actual en la navegación del layout privado de C-05).

| Estado | `?state=` | Qué muestra |
|--------|-----------|-------------|
| Datos cargados (4 secciones) | `loaded` | Consultorio + parámetros de reserva, horarios con tramos, prestaciones (incluida una inactiva) y bloqueos. |
| Vacío: horarios | `empty-hours` | Estado vacío de horarios con «Agregar tramo»; el resto cargado. |
| Vacío: prestaciones | `empty-services` | Estado vacío de prestaciones con «Nueva prestación». |
| Vacío: bloqueos | `empty-blocks` | Estado vacío de bloqueos con «Nuevo bloqueo». |
| Vacío: tres secciones | `empty-all` | Horarios, prestaciones y bloqueos vacíos a la vez; consultorio cargado. |
| Error de carga por sección | `error-section` | Solo prestaciones falla: alerta con «Reintentar»; las otras secciones siguen utilizables. |
| Validación: tramos superpuestos | `hours-overlap` | Lunes 09:00–13:00 y 12:30–18:00: ambos campos marcados, error con ícono + texto, sin guardado. |
| Validación: tramo invertido | `hours-inverted` | Lunes 13:00–09:00: error de fin posterior al inicio, sin guardado. |
| Duración no múltiplo | `service-duration` | Formulario de prestación con 20 min frente a granularidad 15: error por campo, lista sin cambios. |
| Confirmar baja | `service-deactivate` | Diálogo «¿Dar de baja «Limpieza»?»: baja lógica, sin borrado físico. |
| Reactivación | `service-reactivate` | Prestación inactiva reactivada: aviso de éxito y visibilidad pública conservada. |
| Bloqueo con turnos afectados | `block-affected` | Aviso con 2 turnos (solo horas, sin IDs ni datos de pacientes) y copy honesto para C-11. |
| Conflicto 409 | `block-overlap-409` | `SCHEDULE_BLOCK_OVERLAP`: aviso de solape con formulario conservado. |
| Confirmación de eliminación | `delete-confirm` | Diálogo «¿Eliminar este bloqueo?» antes de `DELETE`. |
| Guardado con éxito | `save-success` | Éxito en consultorio y horarios; bloqueos conservan un borrador sin guardar (D1). |
| Sesión vencida | `session-expired` | 401: sin datos parciales; deriva a `/login?next=%2Fconfiguracion` (C-06). |
| Portapapeles disponible | `clipboard-ok` | Botón «Copiar link público de reserva» + aviso `role="status"`. |
| Fallback del link | `clipboard-fallback` | Sin portapapeles: campo `<input readonly>` con la URL y aviso para copiar manualmente. |

Archivos:

| Archivo | Contenido |
|---------|-----------|
| `settings.html` | Todas las pantallas/estados; selector y `?state=`; sprite local de íconos. |
| `settings.css` | Reglas del mockup: solo `var(--…)` de `tokens.css`; medidas locales `--layout-*` y `--settings-*` sin token en Revision 1. |
| `previews/<estado>-390.png` | Captura renderizada a 390×844, escala CSS (página completa salvo los dos estados de diálogo). |
| `previews/<estado>-1440.png` | Captura renderizada a 1440×900, escala CSS (página completa salvo los dos estados de diálogo). |

## Cómo verlo

`file://` está bloqueado en el entorno de render de este checkout; servir la raíz del repo:

```bash
# desde la raíz del repo (cualquiera de los dos)
npx serve -l 8765
python -m http.server 8765 --bind 127.0.0.1
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-19-settings-ui/mockups/settings.html?state=loaded
#   http://127.0.0.1:8765/openspec/changes/c-19-settings-ui/mockups/settings.html?state=service-deactivate
```

En un navegador normal también se puede abrir `settings.html` con doble clic; las rutas relativas a `design-system/` funcionan con `file://` fuera de este harness. Probar a **390×844** y **1440×900** (DevTools → modo dispositivo). El selector de la barra superior cambia de estado y actualiza la URL; detener el servidor con Ctrl+C.

Las capturas se generaron con Playwright MCP: `browser_navigate` a cada URL `?state=`, `browser_resize` a 390×844 y 1440×900, y `browser_take_screenshot` con `scale: "css"` en `previews/`. `fullPage: true` para los 16 estados de página y `fullPage: false` para `service-deactivate` y `delete-confirm` (así el diálogo queda centrado y el fondo atenuado cubre todo el viewport). La revisión automatizada comprobó en los 36 casos: estado correcto, sin scroll horizontal, variantes visibles esperadas, recursos locales 200 y sin errores de consola. Los defectos detectados se corrigieron antes de entregar.

## Fuentes

- `design-system/README.md`, `tokens.css`, `brand.js`, `components.html` y `assets/logo.svg` — Revision 1.
- `openspec/changes/archive/2026-11-21-c-05-frontend-core-shell/mockups/private-shell.html` y `frontend/src/styles/layout.css` — shell privado (header, navegación, bottom-nav mobile / sidebar desktop).
- `openspec/changes/archive/2026-11-21-c-06-login-ui/mockups/login-expired.html` — destino de la sesión vencida.
- `openspec/changes/archive/2026-11-22-c-07-services-catalog/specs/services-catalog/spec.md` — nombres, visibilidad, baja lógica y 422.
- `openspec/changes/archive/2026-11-22-c-08-working-hours-booking-settings/specs/booking-settings/spec.md` — parámetros y límites.
- `openspec/changes/archive/2026-11-22-c-09-schedule-blocks/specs/blocked-ranges/spec.md` — instantes, rango semiabierto y turnos afectados.
- Especificaciones del change: `specs/settings-ui/spec.md`, `specs/spa-routing/spec.md`; `design.md` D1–D8 y §UI Mockups.

## Placeholders `(TBD)`

- Logo: el SVG de `design-system/assets/logo.svg` está rotulado **PROVISIONAL** y se muestra intacto en el header y el favicon.
- Nombre del profesional, dominio del link público, dirección y textos finales del consultorio: datos de ejemplo.
- Edición de zona horaria y detalle de turnos afectados al modificar horarios: `(TBD)` de `design.md`.
- El enlace «Ir a ingresar» del estado de sesión vencida apunta al mockup de C-06; el destino real es responsabilidad del router.

## Notas de diseño

- Mobile-first: una columna, tarjetas apiladas, acciones a ancho completo, tablas convertidas en listas, sin scroll horizontal. Desktop (≥ 64rem): índice lateral de secciones, tablas para prestaciones y bloqueos.
- Inputs del flujo a 16 px y 48 px de alto (`--text-base`, `--control-height-lg`); foco con `--focus-ring`; movimiento solo con tokens y `prefers-reduced-motion`.
- Errores: borde de 2 px + ícono + texto y `aria-describedby`; alertas con `role="alert"` / `role="status"`; estados nunca solo por color.
- Los diálogos se representan visualmente con una capa estática para poder capturarlos; la implementación usa `<dialog>` nativo con foco devuelto al control que lo abrió (D8).
- El render seguro se aplica en código: el texto hostil se inserta como nodo de texto, nunca con `innerHTML`.

## Aprobación

Aprobados explícitamente por el usuario el 2026-11-22 (Revision 1 del design system, mockup revision 1). Registrado en este archivo y en `design.md` §UI Mockups. Queda habilitado `/opsx:apply`; cualquier cambio visual posterior reinicia la aprobación a `pending`.
