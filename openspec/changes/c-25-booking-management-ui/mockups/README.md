# Mockups — C-25 `booking-management-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Approval: pending (aprobador: Lucas)
Mockup revision: 1
Preview: rendered (2026-11-23, `previews/`)

Todas las pantallas usan el app shell público aprobado en C-05 (header con logo provisional y "Acceso profesionales", pie con `(TBD)`), sin cambios, y reutilizan lo aprobado en C-24 (tarjeta de confirmación, `.meta-list`, insignias, selector de día/horario, `.sticky-cta`, alertas, `.empty`) y el diálogo de confirmación de C-19. Textos y códigos de error según el contrato **real** de C-15 (design D2, contrastado con el código).

Datos de ejemplo: hoy = lunes 23/11/2026; turno de **Limpieza** (45 min) el **martes 24/11 10:30 – 11:15**, Consultorio La Muela `(TBD)`; reprogramación al **jueves 26/11 11:15 – 12:00**.

## Pantallas

Los botones y enlaces navegan entre pantallas para recorrer el flujo ("Reprogramar", "Cancelar turno", "Volver", "Sí, cancelar turno", "Confirmar cambio", "Reintentar"…). Al pie de cada mockup hay una lista "Pantallas" (solo del mockup) para saltar a cualquiera.

| Archivo | Ruta | Estado mostrado |
|---------|------|-----------------|
| `loading.html` | `/turno/{token}` | `GET` en curso: "Cargando tu turno…" (`role="status"`) y esqueleto, sin acciones |
| `detail.html` | `/turno/{token}` | 200 con banderas en `true`: prestación, día, horario de Buenos Aires, consultorio, insignia "Confirmado", "Reprogramar" y "Cancelar turno", aviso "Este enlace es personal" |
| `past.html` | `/turno/{token}` | Banderas en `false` (`CONFIRMED` ya comenzado, `COMPLETED`, `NO_SHOW`): solo lectura con insignia "Turno pasado" |
| `cancelled-readonly.html` | `/turno/{token}` | `status: CANCELLED` (cancelado por el consultorio; el token sigue vigente): solo lectura, "Reservar un turno" |
| `invalid-token.html` | `/turno/{token}` | 404 `INVALID_OR_EXPIRED_TOKEN` o token con forma imposible: mensaje genérico, no revela si el turno existió |
| `rate-limited.html` | `/turno/{token}` | 429 `RATE_LIMITED` con `Retry-After` (30 operaciones con token/min por IP) |
| `connection-error.html` | `/turno/{token}` | Red/timeout/5xx en la consulta: "No pudimos cargar tu turno." + "Reintentar" |
| `cancel-confirm.html` | `/turno/{token}` | Diálogo modal "¿Cancelar este turno?" con foco inicial en "No, mantener turno" |
| `cancelling.html` | `/turno/{token}` | `DELETE` en curso: "Cancelando…", "Reprogramar" deshabilitado |
| `cancelled.html` | `/turno/{token}` | 200 del `DELETE`: "Turno cancelado", aviso de email y de que el enlace ya no sirve |
| `not-modifiable.html` | `/turno/{token}` | 422 `BOOKING_MODIFICATION_NOT_ALLOWED`: aviso + estado actual en solo lectura |
| `cancel-unknown.html` | `/turno/{token}` | Red/timeout/5xx en el `DELETE`: no afirma ni niega la cancelación (403 `CSRF_REQUIRED` usa el mismo patrón con "La página quedó desactualizada") |
| `reschedule.html` | `/turno/{token}?accion=reprogramar` | Selector de C-24 para la misma prestación, turno actual visible, jue 26 · 11:15 elegido, "Confirmar cambio" al pie |
| `reschedule-submitting.html` | `/turno/{token}?accion=reprogramar` | `POST …/reschedule` en curso: "Reprogramando…", selector inerte |
| `reschedule-conflict.html` | `/turno/{token}?accion=reprogramar` | 409 `SLOT_TAKEN`: "Tu turno sigue el martes 24…", disponibilidad recargada y "Horarios cercanos" |
| `rescheduled.html` | `/turno/{token}` | 200 del `POST`: "¡Turno reprogramado!" con datos de la respuesta y "Antes: mar 24/11 · 10:30" |
| `reschedule-unknown.html` | `/turno/{token}?accion=reprogramar` | Red/timeout/5xx/400 en el `POST`: no afirma ni niega el cambio; "Ver estado actual" |
| `reschedule-unavailable.html` | `/turno/{token}?accion=reprogramar` | Prestación sin disponibilidad pública o no identificable (design D5, pedido P1): "No se puede reprogramar este turno online" |
| `manage.css` | — | Copia de `booking.css` de C-24 + sección "Gestión de turno" (design D9); solo variables de `design-system/tokens.css` |

Capturas (viewport, escala CSS 1, página completa salvo `cancel-confirm`, que es solo el viewport por el diálogo fijo) en `previews/`: `<pantalla>-390.png` (390x844, emulación mobile) y `<pantalla>-1440.png` (1440x900), una por archivo HTML (36 en total).

## Fuentes

- `design-system/tokens.css`, `brand.js`, `assets/logo.svg`, `components.html` (Insignias de estado, Botones, Alertas, Estado vacío, Tarjeta, Reserva pública) — Revision 1.
- Mockups aprobados de C-24 (`openspec/changes/archive/2026-11-23-c-24-public-booking-ui/mockups/`) y diálogo de confirmación de C-19 (`frontend/src/core/dom/confirm.ts`, `styles/settings.css`).
- `knowledge-base/06_funcionalidades.md` §US-031, US-032, `07_flujos_principales.md` §Flujo 4, `05_reglas_de_negocio.md` §RN-RS-04..09, `08_arquitectura_propuesta.md` §Seguridad (Links con token).
- Contrato real: `backend/.../reservas/` (C-15: `BookingManagementController`, `BookingManagementUseCase`, `BookingView`); specs `booking-token-management`, `availability`, `error-responses`.
- Specs del change: `specs/booking-management-ui`, `specs/spa-routing`.

## Placeholders `(TBD)`

- Datos de contacto del consultorio en "Enlace no válido", "No se puede reprogramar online" y el pie (design system TBD 8), heredados de C-05.
- Logo definitivo (rotulado PROVISIONAL).
- Prestación, horarios y consultorio de ejemplo.

## Cómo verlo

`file://` puede bloquear `tokens.css`; serví la raíz del repo:

```bash
# desde la raíz del repo (cualquiera de los dos)
npx serve -l 8765
python -m http.server 8765 --bind 127.0.0.1
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-25-booking-management-ui/mockups/detail.html
#   http://127.0.0.1:8765/openspec/changes/c-25-booking-management-ui/mockups/cancel-confirm.html
#   http://127.0.0.1:8765/openspec/changes/c-25-booking-management-ui/mockups/reschedule.html
#   http://127.0.0.1:8765/openspec/changes/c-25-booking-management-ui/mockups/reschedule-conflict.html
#   http://127.0.0.1:8765/openspec/changes/c-25-booking-management-ui/mockups/invalid-token.html
```

(con `npx serve` la URL es `http://localhost:8765/...`; `serve` puede quitar la extensión `.html`.) Probar a 390x844 y 1440x900 (DevTools → modo dispositivo). Detener el servidor con Ctrl+C.

## Notas de revisión

- **Token**: queda en la URL (formato del enlace del email; permite recargar), pero nunca en el título ("Gestionar turno" fijo), el texto, la consola, el `storage` ni otras peticiones; `Referrer-Policy: no-referrer` ya vigente (design D4). La pantalla avisa "Este enlace es personal: no lo compartas".
- **Acciones según el servidor**: "Reprogramar"/"Cancelar turno" aparecen solo si `reschedulable`/`cancellable` vienen en `true`; no se decide con el reloj del navegador.
- **Cancelar** es destructivo e irreversible: botón con contorno de peligro en el detalle y confirmación modal con el foco inicial en la opción segura ("No, mantener turno"); el botón rojo lleno solo aparece dentro del diálogo.
- **"Turno pasado"** en lugar de "Atendido"/"Ausente": al paciente no se le informa asistencia ni ausencia.
- **Reprogramar** reutiliza el selector de C-24 tal cual (tira de 14 días desde hoy + "Más días", Mañana/Tarde, horarios cercanos tras 409). El fin mostrado al elegir sale del slot; la pantalla de éxito usa el fin real de la respuesta (duración del turno, design P3).
- **Tras cancelar**, recargar muestra "Enlace no válido" (C-15 revoca el token); el texto de éxito lo anticipa.
- En el mockup los botones de navegación son enlaces (`<a>`) para recorrer el flujo; en la app son `<button>` con los mismos estilos.
- Errores con ícono + texto + borde (no dependen solo del color); avisos de servidor como alerta arriba del contenido.
