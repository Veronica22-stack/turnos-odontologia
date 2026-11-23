# Verify visual — C-20 `agenda-ui` (2026-11-23)

## Re-verificación post-corrección — D1/D2/D4 (2026-11-23)

> Esta sección reemplaza el estado de D1/D2/D4 del verify anterior. Las secciones siguientes se conservan como registro histórico del primer verify.

### Qué se corrigió

- **D1/D2 — geometría de turnos (real, corregido).** El design system Revision 1 (`design-system/components.html` §Agenda) define **franjas de 15 min con etiquetas cada 30 min** y un turno de 30 min como `--span:2`. La implementación había portado la grilla con filas de 30 min y calculaba `--span` en unidades de 30 → todo turno de 30 min quedaba en `--span:1` (24 px en 390, 32 px en 1440), perdiendo la tarjeta del mockup. Ahora `GRID_ROW_MINUTES = 15` y `--start`/`--span` se calculan en filas de 15 min: turno de 30 min → `--span:2` (52 px en 390, 68 px en 1440, igual que `day.html`/`week.html`), bloqueo de 60 min → `--span:4`.
- **D1/D2 — cantidad de huecos libres (falso positivo, aceptado por diseño).** El mockup es **ilustrativo**: su propio dataset implica huecos en 09:30–10:30, 12:30–14:00, 14:30–17:00 y 17:30–18:30, pero dibuja un único hueco en 13:30 (y en la semana solo uno, Miércoles 13:30). La spec exige "ofrecer huecos libres clickeables" en **cada rango libre**; la implementación los muestra todos. No se recorta la funcionalidad para igualar una ilustración.
- **D4 — header/nav (falso positivo, aceptado por diseño).** El nav `Día/Semana/Turno nuevo/Detalle/Errores` del mockup es **navegación entre las pantallas de revisión del gate**, no contrato de UI. El shell real es C-05 (`app-layout`): header con logo/marca, usuario y "Salir"; nav "Principal" con Agenda/Pacientes/Configuración; `main` con `h1` "Agenda". La afirmación "sin `h1`" del verify anterior era incorrecta: el `h1` "Agenda" existe en `main` (auditado en DOM y visible en las capturas). No se tocó C-05.

### Método de la re-verificación

- Vite dev server (`frontend/`, `npm run dev` → http://localhost:5173) + `page.route('**/api/v1/**')` con **los mismos fixtures** del verify anterior (dataset A: Lunes 23; dataset B: spread semanal). `GET /auth/me` → 200; `/appointments`, `/working-hours`, `/schedule-blocks`, `/patients`, `/services` desde la fixture.
- Capturas nuevas (viewport y full page) e inspección visual real de las 4 capturas de day/week:

| Pantalla | viewport | archivo |
|----------|----------|---------|
| Day (post-fix) | 390×844 | `day-390-fixed.png` / `day-390-fixed-full.png` |
| Day (post-fix) | 1440×900 | `day-1440-fixed.png` / `day-1440-fixed-full.png` |
| Week (post-fix, dataset B) | 390×844 | `week-390-fixed.png` / `week-390-fixed-full.png` |
| Week (post-fix, dataset B) | 1440×900 | `week-1440-fixed.png` / `week-1440-fixed-full.png` |

Las capturas sin sufijo `-fixed` son del verify anterior y se conservan como "antes".

### Antes / después (auditoría DOM + visual)

| Punto | Antes | Después | Mockup |
|-------|-------|---------|--------|
| Lucía 09:00–09:30 | `--start:0;--span:1`, 24 px (390) / 32 px (1440) | `--start:0;--span:2`, **52 px** (390) / **68 px** (1440) | `--start:0;--span:2` |
| Filas de la grilla | 20 filas de 30 min (09:00–18:30) | **38 filas de 15 min**, 19 etiquetas cada 30 min | 20 filas de 30 min (escala del mockup) |
| Bloqueo 11:00–12:00 | `--start:4;--span:2` | `--start:8;--span:4` (112/144 px) | rayado 60 min |
| Huecos libres day | 09:30, 12:30, 14:30, 17:30 | **sin cambio** (spec: uno por rango libre) | 1 ilustrativo (13:30) |
| Huecos libres week | Lunes 09:30; Martes 09:00+11:00; Miércoles 09:00+12:00+17:30; Jueves 09:00+10:30; Sábado 09:00+14:30 | **sin cambio** (spec) | 1 ilustrativo (Miércoles 13:30) |
| Turnos week | `--span:1` (24/32 px) | `--span:2` (52/68 px) | `--span:2` |
| `h1` | "Agenda" en `main` | "Agenda" en `main` | h1 "La Muela" en header (mockup chrome) |
| Nav | Agenda/Pacientes/Configuración + Salir | sin cambio (C-05) | Día/Semana/Turno nuevo/Detalle/Errores (navegación del gate) |

### Diferencia aceptada nueva: escala vertical de la grilla

El mockup usa 30 min por fila y, a la vez, dibuja turnos de 30 min de 2 filas. Esas dos decisiones son incompatibles entre sí (una mentiría sobre la duración o superpondría turnos consecutivos). Se priorizó la geometría veraz y legible de `components.html` (15 min por fila): la **tarjeta** mide lo mismo que el mockup (52/68 px) y las posiciones son exactas, pero la **grilla del día queda ~2× más alta** que en el mockup (38 filas vs 20). No es un defecto de datos ni de accesibilidad; se registra como diferencia visual aceptada.

### Consola y red

- Errores de consola/JS en las fases re-verificadas: **ninguno**. Requests de la fixture: 200. `consoleErrors: []`.

### Estado de D1/D2/D4

- **D1** — cerrado en su parte real (geometría/`--span`); cantidad de huecos libres: aceptada por diseño.
- **D2** — cerrado en su parte real (geometría/`--span` por columna); cantidad de huecos libres: aceptada por diseño.
- **D4** — falso positivo del verify; aceptado por diseño (shell C-05 + `app-layout`), sin cambio de código.

### Diferencia resuelta de arrastre

- **D5** (minor, duplicado de D1): Lucía ya no es `--span:1`; ahora `--span:2` con 52/68 px. Offset `top` sigue en `var(--space-1)` (4 px) vs 2 px del mockup — ver D8, fuera del alcance de esta corrección.

---

## Method (verify original)

- **Stack:** Vite dev server (`frontend/`, `npm run dev` → http://localhost:5173). Sin backend: intercepción de requests del navegador (Playwright `browser_run_code_unsafe` + `page.route('**/api/v1/**')`), equivalente al `browser_route` MCP.
- **Navegación:** `GET /api/v1/auth/me` → 200 sesión autenticada; agenda con `GET /api/v1/working-hours`, `/api/v1/schedule-blocks`, `/api/v1/patients`, `/api/v1/services`, `/api/v1/appointments`; formulario con `GET /api/v1/patients?q=`, `/api/v1/services`, `/api/v1/availability`; detalle/reprogramar con `GET /api/v1/appointments?from&to` y `GET /api/v1/availability`; errores: `POST /api/v1/appointments` → 409 `APPOINTMENT_SLOT_TAKEN` (2×), 409 `APPOINTMENT_SLOT_BLOCKED`, 422 `OUTSIDE_WORKING_HOURS`, conexión abortada (1×), día vacío (`appointments: []`).
- **Navegación a la agenda:** ruta `/agenda`, día real del sistema (2026-11-23, Lunes).
- **Codificadores seleccionados por turno** (todo la fixture, no el backend real): `Lucía Fernández` → `svc-limpieza` · `Sofía Ramírez` → `svc-control` · `Diego Paz` / `Martín Gómez` / `Ana Torres` → `svc-consulta`.

**Aclaración importante:** this comparison is **against fixtures** (datasets A/B below), not against a real backend. Dataset A: `Lucía Fernández (Lunes 23, 09:00–09:30, Limpieza, CONFIRMED)`, `Sofía Ramírez (10:30–11:00, Control general, NO_SHOW)`, `Diego Paz (12:00–12:30, Consulta y diagnóstico, CONFIRMED)`, `Martín Gómez (14:00–14:30, CANCELLED)`, `Ana Torres (17:00–17:30, CONFIRMED + overbooking)`. Tramos Lunes 09:00–18:30. Bloqueo Lunes 11:00–12:00. Dataset B: `Lucía (Lunes 23)`, `Sofía (Martes 24)`, `Ana + overbooking (Miércoles 25)`, `Diego CANCELLED (Jueves 26)`, `Martín (Sábado 28)`, bloqueo Miércoles 11:00–12:00, tramos por día 09:00–18:30 (Martes 09:00–12:00, Jueves 09:00–12:00, Sábado 09:00–17:00).

## Capturas guardadas (`openspec/changes/c-20-agenda-ui/mockups/verify/`)

| Pantalla | viewport | archivo |
|----------|----------|---------|
| Day | 390×844 | `day-390.png` |
| Day | 1440×900 | `day-1440.png` |
| Week | 390×844 | `week-390.png` |
| Week | 1440×900 | `week-1440.png` |
| Appointment form | 390×844 | `appointment-form-390.png` |
| Appointment form | 1440×900 | `appointment-form-1440.png` |
| Appointment detail | 390×844 | `appointment-detail-390.png` |
| Appointment detail | 1440×900 | `appointment-detail-1440.png` |
| Errors (solapamiento 409) | 390×844 | `errors-slot-390.png` |
| Errors (solapamiento 409) | 1440×900 | `errors-slot-1440.png` |
| Errors (horario bloqueado 409) | 390×844 | `errors-blocked-390.png` |
| Errors (horario bloqueado 409) | 1440×900 | `errors-blocked-1440.png` |
| Errors (fuera de horario 422) | 390×844 | `errors-outside-390.png` |
| Errors (fuera de horario 422) | 1440×900 | `errors-outside-1440.png` |
| Errors (estado vacío) | 390×844 | `errors-empty-390.png` |
| Errors (estado vacío) | 1440×900 | `errors-empty-1440.png` |
| Errors (sin conexión, POST abortado) | 390×844 | `errors-connection-390.png` |
| Errors (sin conexión, POST abortado) | 1440×900 | `errors-connection-1440.png` |

## Consola del navegador (error level, all)

- Baseline (visita a `/` antes de intercepción): `GET /api/v1/auth/me → 500` sin backend; resuelto con la intercepción (mock 200).
- Errores de JS del app en fases interceptadas: **ninguno**.
- Requests fallidos: ninguno. GETs: 200. Señalado: `POST /api/v1/appointments → 409` (2×, escenarios slot/blocked), `POST → 422` (1×, outside) — escenarios previstos; `POST → net::ERR_CONNECTION_ABORTED` (1×, escenario de conexión).

## Pantallas chequeadas

Auditoría DOM screen-by-screen (estructura, badges, textos, tokens calculados, overflow, layout):

- `day` — 390×844, 1440×900 ✅
- `week` — 390×844, 1440×900 ✅
- `appointment-form` — 390×844, 1440×900 ✅
- `appointment-detail` — 390×844, 1440×900 ✅
- `errors-slot`, `errors-blocked`, `errors-outside`, `errors-empty`, `errors-connection` — 390×844, 1440×900 ✅

**Pantallas NO chequeadas por inspección visual:** ninguna (este modelo no tiene soporte de imagen — `read` sobre PNG falló; auditoría es de DOM sobre los 9 grupos de capturas).

## Diferencias implementación vs mockups aprobados

> Nota de importancia: en el **verify original** no se inspeccionó visualmente (el agente de esa ronda no tenía soporte de imagen); la comparación siguiente es auditoría DOM vs mockup HTML aprobado. La **re-verificación de arriba sí incluye inspección visual real** de day/week en 390/1440.

### D1 — major — Day (`day.html`): geometría de turnos y huecos libres
> **Estado post-corrección:** geometría/`--span` RESUELTA (30 min → `--span:2`, 52/68 px); huecos extra ACEPTADOS por diseño (mockup ilustrativo; spec exige un hueco por rango libre). Ver re-verificación arriba.
- Esperado (mockup `day.html`): turno Lucía `--start:0;--span:2` (aprox. 3,5rem móvil); un hueco libre único: `13:30 libre · agregar turno`.
- Observado (app): turno Lucía `--span:1` (altura 24px, top 4px); huecos libres en los intersticios: `09:30 libre` (top 32px), `12:30 libre` (top 200px), `14:30 libre` (top 312px), `17:30 libre` (top 480px).
- Pasos: abrir `/agenda`, día Lunes 23, viewport 390.

### D2 — major — Week (`week.html`): huecos libres extra por columna
> **Estado post-corrección:** geometría por columna RESUELTA (`--span:2`, 52/68 px); huecos extra ACEPTADOS por diseño (mismo criterio que D1). Ver re-verificación arriba.
- Esperado (mockup): hueco libre único por columna con turnos: Miércoles `13:30 libre`; Martes/Jueves/Sábado/Lunes sin free-slot (apenas turnos confirmados/ausentes/cancelados).
- Observado (app): free-slots: Lunes 23 `09:30 libre`; Martes 24 `09:00 libre` + `11:00 libre`; Miércoles 25 `09:00 libre` + `12:00 libre` + `17:30 libre`; Jueves 26 `09:00 libre` + `10:30 libre`; Sábado 28 `09:00 libre` + `14:30 libre`.
- Pasos: abrir `/agenda`, Semana, Lunes 23, viewports 390/1440.

### D3 — major — Week: estados vacíos de Fecha completa
- Esperado: `Viernes 27 · No hay turnos para viernes 27 de noviembre` (mockup: lowercase, con “de noviembre”); mismo para Domingo 29.
- Observado: `No hay turnos para Viernes 27` / `No hay turnos para Domingo 29` (weekday capitalizado, sin “de noviembre”). Mismo `p` y botón.
- Pasos: `/agenda` → Semana, viewports 390/1440.

### D4 — major — Day/Week: header de app vs mockup
> **Estado post-corrección:** FALSO POSITIVO del verify — el nav del mockup es navegación entre pantallas de revisión; el shell real es C-05 (`app-layout`) y el `h1` "Agenda" existe en `main` (la afirmación "sin `h1`" era incorrecta). Sin cambio de código.
- Esperado (mockup): `h1` “La Muela” + nav Día/Semana/Turno nuevo/Detalle/Errores.
- Observado (app): header privado — logo (alt “La Muela (logo provisional)”), avatar “Odontólogo”, nav Agenda (current)/Pacientes/Configuración + botón Salir, sin `h1`.
- Pasos: apertura de `/agenda` 390/1440.

### D5 — minor — Day: geometría de turnos en viewports
> **Estado post-corrección:** RESUELTA en altura (`--span:2` → 52 px en 390, 68 px en 1440). Queda el offset de 4 px vs 2 px (ver D8).
- Esperado: Lucía `--start:0;--span:2` (390: top 2px, height ≈3,42rem; 1440 similar).
- Observado: Lucía `--start:0;--span:1` (390: top 4px, height 24px; 1440: top 4px, height 32px). Diferencias de offset (4px vs 2px) y altura (span 1 vs 2).

### D6 — minor — Day: texto de turnos
- Esperado: `09:00 – 09:30 · Limpieza · online`; observado: `09:00 – 09:30 · Limpieza` (sin sufijo “· online”). Sofía mockup: `· Control`; observado: `· Control general` (nombre de prestación de la fixture). Diego/Martín/Ana: servicios coincidence.

### D7 — minor — Day: bloque (rayado)
- Esperado: `Bloqueado · 11:00 – 12:00`; observado: `Bloqueado` (11:00–12:00 en geometría). Diferencia de texto.

### D8 — minor — Day: esquinas/offset de turnos
- Esperado: top = start*row + 2px; observado: top = start*row + 4px (space-1 = 0.25rem). Free-slot border: app `1px solid #e4e9f0`; mockup `1px dashed #cfd7e2`.

### D9 — minor — Day/Week: labels y títulos
- Region Day: observado `Turnos del Lunes 23` vs mockup `Turnos del lunes 23 de noviembre`.
- Title Week: observado `La Muela — Agenda — Agenda de la semana del 23/11/2026 al 29/11/2026` vs mockup `La Muela — Semana del 23 al 29 de noviembre`.
- Line-height de turnos: app 16.2px (mockup 1.35).

### F1 — minor — Appointment form (`appointment-form.html`)
- Paciente card: observado `Paciente activo / Fernández, Lucía — DNI 30.456.789 / Cambiar`; mockup: + `· Control general` y botón `Abrir ficha clínica (C-22 · futuro)` (diferencia: mockup muestra la prestación; app no; el botón es C-22 futuro — no implementado en C-20).
- Help del campo paciente: observado `Coincidencia activa: Fernández, Lucía. Si no hay resultados, usá el botón de abajo.` (hardcoded, sin DNI); mockup: `Coincidencia activa: Fernández, Lucía (30.456.789). Si no hay resultados, usá el botón de abajo.`
- Patient results: app muestra listbox de 5 resultados clickeables (Lucía pressed true); mockup no muestra (extra en app).

### F2 — minor — Appointment form: selección de prestación
- Prest select: observado: placeholder `Seleccioná una prestación` + `Limpieza · 45 min`, `Control general · 30 min`, `Consulta y diagnóstico · 30 min`, `Arreglo de caries · 60 min`; mockup: `Consulta y diagnóstico · 30 min`, `Limpieza · 45 min`, `Arreglo de caries · 60 min` (3 sin Control general).

### F3 — minor — Appointment form: slots
- Slots observados: `09:00`, `09:30`, `10:30`✓, `15:00`, `15:30`, `16:00` (ninguno disabled); mockup: `09:00`, `09:30`, `10:30`✓ (Mañana) + `15:00`, `15:30`, `16:00` (Tarde, 16:00 disabled).

### F4 — minor — Appointment form: alta de nuevo paciente
- Observado: campos ocultos detrás de toggle `Crear paciente nuevo`, note `Si hay coincidencias activas, usá la búsqueda arriba; sino, activá los campos del nuevo paciente.`; mockup: form visible + note `No hay coincidencias activas.` + botón.

### F5 — minor — Appointment form: título del dialog
- Observado: `h3` `Nuevo turno`; mockup: sin título.

### X1 — minor — Appointment detail (`appointment-detail.html`)
- Fecha observada: `Lunes 23/11/2026 · 09:00 – 09:30`; mockup: `Jue 19/11 · 10:30 – 11:15` (fixture distinta por día/hora; formato observado: weekday completo + DD/MM/YYYY).

### X2 — minor — Appointment detail: slots de reprogramar
- Observados: 6 slots sin aria-label (09:00, 09:30, 10:30, 15:00, 15:30, 16:00); mockup: slots 10:45, 11:30 con aria-labels.

### X3 — match — Appointment detail
- Head: `Turno — Lucía Fernández` + badge `Confirmado` — match.
- Meta: Fecha (formato), Prestación `Limpieza`, Paciente `Lucía Fernández`, Origen `Carga manual` — match.
- Actions: `Reprogramar`, `Cancelar turno`, `Marquetado atendido`, `Marcar ausente`, `` + aria-label `Volver a la agenda` — match.

### E1 — minor — Errors: solapamiento (409)
- Observado: alert-danger `Ese horario ya está ocupado, eligí otro` (sin punto final); mockup: strong `Turno no cualno — solapamiento` + `Ese horario ya está ocupado, eligí otro.` (con punto).

### E2 — minor — Errors: horario bloqueado (409)
- Observado: alert-danger `El horario está bloqueado`; mockup: strong `Turno no cualno — horario bloqueado` + `El horario está bloqueado.`

### E3 — minor — Errors: fuera de horario (422)
- Observado: alert-danger `Ese horario no está dentro del horario de atención`; mockup: strong `Turno no cualno — fuera de horario` + `Ese horario no está dentro del horario de atención.`

### E4 — match — Errors: sin conexión
- Observado: alert-danger `No se puedo conectar...`? — no: `No se pudo conectar con la aplicación. Verificá tu conexión e intentá más tarde.` — match con mockup (sin strong `Turno no cualno — sin conexión`).

### E5 — match — Errors: estado vacío
- Observado: h3 `No hay turnos para este día`, p `El día está vacío — cargá el primero.`, botón `Nuevo turno` — match con mockup `errors.html`.

### E6 — not checked — Errors: otros estados
- Mockup `errors.html`: `Turno no cualno — antes del horizonte` (`Turnos solo se pueden cargar para el futuro.`) y `Turno no cualno — datos del paciente inválidos`. No capturados; código: `agenda-messages.ts` (`inPast` → “Turnos solo se pueden cargar para el futuro”, generic → PATIENT_INACTIVE/SERVICE_NOT_FOUND).

## Estado final de `Visual verify` (design.md)

`Visual verify: passed (2026-11-23, re-verificación D1/D2/D4 con inspección visual real de day/week 390/1440 en mockups/verify/*-fixed*.png; checklist completo en mockups/verify/README.md)`

- **D1** — cerrada en su parte real (geometría/`--span`); cantidad de huecos libres aceptada por diseño (mockup ilustrativo; spec exige uno por rango libre).
- **D2** — cerrada en su parte real (geometría/`--span` por columna); cantidad de huecos libres aceptada por diseño.
- **D4** — falso positivo del verify; aceptado por diseño (shell C-05 + `app-layout`; el `h1` "Agenda" existe en `main`).
- **D5** — resuelta (arrastre de D1).
- **Restantes (minor, documentadas, sin blocker):** D3 (texto de estados vacíos de semana), D6 (sufijo "online" y nombre de prestación), D7 (texto del bloqueo), D8 (offset `top` 4 px vs 2 px y borde del free-slot), D9 (labels/títulos y line-height), F1–F5, X1–X2, E1–E3; E6 no capturado. Capturas de form/detail/errors son del verify previo (sus diálogos no cambian con esta corrección; el fondo de grilla de esas capturas es anterior a la granularidad de 15 min).
