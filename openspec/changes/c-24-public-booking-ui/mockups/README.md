# Mockups — C-24 `public-booking-ui`

Design system: Revision 1 (aprobada 2026-11-16) · Política: `system-and-mockups`
Approval: approved (2026-11-23, by Lucas)
Mockup revision: 1
Preview: rendered (2026-11-23, `previews/`)

Todas las pantallas usan el app shell público aprobado en C-05 (header con logo provisional y "Acceso profesionales", pie con `(TBD)`), sin cambios, y el patrón "Reserva pública — selector de horario" de `design-system/components.html` (stepper, `.service-option`, `.day-strip`, `.slots`, `.sticky-cta`, `.empty`). Textos y códigos de error según el contrato **real** de C-14 (design D2, contrastado con el código).

## Pantallas

| Archivo | Ruta | Estado mostrado |
|---------|------|-----------------|
| `step1-service.html` | `/reservar` | Paso 1 de 3: prestaciones de `GET /public/services` (nombre, duración, descripción opcional), "Limpieza" elegida, "Continuar" al pie |
| `step2-schedule.html` | `/reservar` | Paso 2 de 3: tira de 14 días desde hoy + "Más días", martes 24 elegido, horarios Mañana/Tarde en hora de Buenos Aires, 10:30 elegido, resumen "mar 24/11 · 10:30 – 11:15" |
| `step2-loading.html` | `/reservar` | Paso 2 consultando un día: "Buscando horarios…" (`role="status"`) y grilla esqueleto |
| `step2-empty.html` | `/reservar` | Paso 2, día sin horarios (`slots: []`): estado vacío con "Ver el día siguiente"; "Continuar" deshabilitado |
| `step3-details.html` | `/reservar` | Paso 3 de 3: resumen del turno, nombre, apellido, DNI, teléfono, email, casilla del aviso de privacidad `(TBD · PA-10)`; honeypot `website` presente pero imperceptible |
| `step3-errors.html` | `/reservar` | Validación local (sin llamar a la API): DNI corto, email sin dominio, aviso sin aceptar |
| `step3-submitting.html` | `/reservar` | Enviando: "Reservando…" con spinner y `aria-busy`, campos de solo lectura |
| `slot-taken.html` | `/reservar` | 409 `SLOT_TAKEN` (también 422 `BOOKING_TOO_SOON`/`BOOKING_BEYOND_HORIZON`/`APPOINTMENT_IN_PAST`): vuelve al paso 2, aviso, disponibilidad recargada y "Horarios cercanos" (3 sugerencias); los datos del paso 3 se conservan |
| `rate-limited.html` | `/reservar` | 429 `RATE_LIMITED` con `Retry-After` (30 reservas/min por IP): "Demasiados intentos seguidos… Probá de nuevo en 1 minuto.", datos conservados |
| `contact-limit.html` | `/reservar` | 422 `BOOKING_CONTACT_LIMIT` (≥ 3 turnos futuros activos por DNI/email/teléfono; texto `(TBD · PA-05)`) |
| `step3-unknown.html` | `/reservar` | Red/timeout/5xx al reservar: aviso que no afirma ni niega la reserva y pide revisar el email |
| `unavailable.html` | `/reservar` | Sin prestaciones públicas (`[]` o `NO_ACTIVE_SERVICES`): "Reservas online no disponibles", sin pasos |
| `confirmation.html` | `/reservar/confirmado` | Tras 201: "¡Turno reservado!", prestación, día, horario, consultorio, badge "Confirmado" y aviso de que el enlace de gestión llega solo por email (muestra el email ingresado) |
| `confirmation-generic.html` | `/reservar/confirmado` | Sin datos en memoria (recarga/acceso directo) o 200 `ACCEPTED` del honeypot: "Revisá tu email", sin datos de turno |
| `booking.css` | — | Solo variables de `design-system/tokens.css`; shell público de C-05, campos/botones/alertas/spinner de C-06 y patrón de reserva de `components.html`; medidas locales `--booking-*` (design D8) |

Capturas (viewport, escala CSS 1, página completa) en `previews/`: `<pantalla>-390.png` (390x844, emulación mobile) y `<pantalla>-1440.png` (1440x900), una por archivo HTML (28 en total).

## Fuentes

- `design-system/tokens.css`, `brand.js`, `assets/logo.svg`, `components.html` (Reserva pública, Formularios, Botones, Alertas, Estado vacío, Tarjeta) — Revision 1.
- Shell público de C-05 y mockups de C-06 (`openspec/changes/archive/2026-11-21-c-06-login-ui/mockups/`).
- `knowledge-base/06_funcionalidades.md` §US-030, `07_flujos_principales.md` §Flujo 3, `03_actores_y_roles.md` §Rutas públicas, `10_preguntas_abiertas.md` §PA-05, PA-10, PA-21.
- Contrato real: `backend/.../reservas/` (C-12, C-14), `backend/.../agenda/infrastructure/PublicServicesController.java` (C-07); specs `public-booking`, `availability`, `public-services`, `error-responses`.
- Specs del change: `specs/public-booking-ui`, `specs/spa-routing`, `specs/local-time-display`.

## Placeholders `(TBD)`

- Texto y enlace del aviso de privacidad (PA-10).
- Texto del límite de turnos por contacto (PA-05).
- Datos de contacto del consultorio en "Reservas no disponibles", la confirmación y el pie (design system TBD 8), heredados de C-05.
- Logo definitivo (rotulado PROVISIONAL).
- Datos personales de ejemplo ficticios (Ana Pérez, `ana@ejemplo.com`); prestaciones y horarios de ejemplo (hoy = lunes 23/11/2026).

## Cómo verlo

`file://` puede bloquear `tokens.css`; serví la raíz del repo:

```bash
# desde la raíz del repo (cualquiera de los dos)
npx serve -l 8765
python -m http.server 8765 --bind 127.0.0.1
# abrir, por ejemplo:
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/step1-service.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/step2-schedule.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/step3-details.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/slot-taken.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/rate-limited.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/unavailable.html
#   http://127.0.0.1:8765/openspec/changes/c-24-public-booking-ui/mockups/confirmation.html
```

(con `npx serve` la URL es `http://localhost:8765/...`; `serve` puede quitar la extensión `.html`.) Probar a 390x844 y 1440x900 (DevTools → modo dispositivo). Detener el servidor con Ctrl+C.

## Notas de revisión

- Mobile-first: en pasos 1 y 2 la acción "Continuar" y el resumen de lo elegido quedan pegados al pie (`.sticky-cta`); en el paso 3 el botón va al final del formulario (el teclado taparía una barra fija). Desktop (≥ 1024 px): tarjeta centrada de 40rem sobre `--color-bg-subtle`, grilla de horarios de 4 columnas, nombre y apellido lado a lado.
- **Diferencias con `components.html`**: el stepper dice "Paso N de **3**" (no 4: el resumen está dentro del paso 3) y los días **no** se muestran deshabilitados "sin horarios" por adelantado — saberlo exige una consulta por día (design D4, pedido P2 a C-14).
- El horario siempre se muestra en hora de Buenos Aires; el `endsAt` del resumen viene del servidor.
- Conflicto (409) en tono de advertencia y con salida inmediata: sugerencias + grilla completa del mismo día, sin preselección.
- La confirmación nunca muestra ni enlaza el token de gestión: la respuesta de C-14 no lo trae y el enlace `/turno/{token}` (C-25) llega solo por email. Se muestra el email ingresado para que la persona detecte un error de tipeo (design OQ-1).
- Errores con ícono + texto + borde de 2 px (no dependen solo del color); errores de servidor como alerta arriba del formulario (`role="alert"`).
