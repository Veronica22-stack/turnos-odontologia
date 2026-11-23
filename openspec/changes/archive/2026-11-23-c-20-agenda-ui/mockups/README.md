# Mockups — C-20 `agenda-ui`

Mockup HTML aprobado (pendiente de revisión) del change OpenSpec `c-20-agenda-ui` — vista diaria y semanal de la agenda del odontólogo.

## Fuentes

- Proposal: `openspec/changes/c-20-agenda-ui/proposal.md`
- Design: `openspec/changes/c-20-agenda-ui/design.md` (§UI Mockups)
- Spec: `openspec/changes/c-20-agenda-ui/specs/agenda/spec.md`
- CHANGES.md (fila C-20)
- Design system: `design-system/README.md` (Revision 1, Approval: approved 2026-11-16)
- Tokens: `design-system/tokens.css` (importado en cada mockup; no se hardcodean colores/espacios/tipografías)
- Brand: `design-system/brand.js` (name `[data-brand-name]`)
- Logo: `design-system/assets/logo.svg` (PROVISIONAL rotulado)
- Componentes reutilizados (clases del design system): `.agenda`, `.appt` (`s-confirmado`/`s-ausente`/`s-cancelado`), `.block`, `.free-slot`, `.now-line`, `.legend`, `.badge` (`badge-confirmado`/`badge-ausente`/`badge-cancelado`/`badge-sobreturno` como estado de turno), `.card`, `.dialog`, `.alert-*`, `.empty`, `.slot`, `.btn`, `.field`, `.input`, `.select`, `.check`, `.appt`, `.agenda-times`, `.agenda-col`, `.line`. Sin badges "reservado" en v1 (no existe `RESERVED` en el modelo, TBD en `design-system/README.md`).

## Pantallas

| Archivo | Pantalla | Cobertura |
|---------|----------|-----------|
| `day.html` | Agenda diaria `/agenda` | Turnos confirmados (badge «Confirmado»), ausente («Ausente»), cancelado («Cancelado»), bloqueo rayado + candado, hueco libre clickeable, línea del día actual, indicación de sobreturno («Sobreturno» en badge naranja), horarios de atención, leyenda |
| `week.html` | Agenda semanal | 7 columnas (23–29 de noviembre), turnos y bloqueos por día, navegación ±semana (semana actual mockup), CTA «Nuevo turno», estados vacíos de viernes/domingo |
| `appointment-form.html` | Alta de turno (US-021) | Paciente activo, buscador de paciente + formulario nuevo de alta, prestación con snapshot de duración, slots de `GET /api/v1/availability` clickeables, checkbox de sobreturno, validación local |
| `appointment-detail.html` | Detalle/gestión (US-022) | `<dialog>` con confirmación de cancelar, reprogramar a slots libres, marcar resultado |
| `errors.html` | Estados de error | `409 APPOINTMENT_SLOT_TAKEN` → "Ese horario ya está ocupado, eligí otro"; `409 APPOINTMENT_SLOT_BLOCKED` → "El horario está bloqueado"; `422 OUTSIDE_WORKING_HOURS` → "Ese horario no está dentro del horario de atención"; `422 APPOINTMENT_IN_PAST` → "Turnos solo se pueden cargar para el futuro"; datos inválidos/inactivos → genérico; sin conexión → genérico; estado vacío con CTA |

## Previsualización

```powershell
# Desde la raíz del repo:
# abrir con un servidor local o directamente el archivo:
start http://127.0.0.1:8787/openspec/changes/c-20-agenda-ui/mockups/day.html
```

Si `python -m http.server 8765` funciona: `python -m http.server 8765 --bind 127.0.0.1` desde la raíz y abrir `http://127.0.0.1:8765/openspec/changes/c-20-agenda-ui/mockups/day.html`. Sin servidor: doble clic en cada archivo en el explorador (funciona con `file://`).

Capturas previas al apply: `mockups/previews/` (Pantalla × {390, 1440}px).

## Decisiones de diseño (registradas)

- overbooking: estado visual del turno = `s-confirmado` + badge amber «Sobreturno» (`badge-sobreturno`, rol de color existente `--color-warning-*` — sin nuevas clases de badge).
- Color del "ahora": `.now-line` del design system (línea roja + punto).
- El turno fuera del tramo (sobreturno) se visualiza igual que confirmado, con el badge de advertencia.

## Aprobación

```
Approval: approved (2026-11-23, by user)
```

Aprobado por el usuario: **SÍ — aprobación explícita confirmada el 2026-11-23** (gate M11 del apply: habilitado con `Approval: approved`).

## Visual verify

```
Visual verify: passed (2026-11-23, re-verificación D1/D2/D4: day/week 390/1440 en mockups/verify/*-fixed*.png)
```

(Gate C-26: comparar implementado contra los mockups aprobados a 390x844 y 1440x900 en `mockups/verify/`. Detalle de la re-verificación y diferencias minor restantes: `mockups/verify/README.md`.)
