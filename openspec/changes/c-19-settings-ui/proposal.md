# Proposal: C-19 `settings-ui`

## Why

Hoy `/configuracion` es un *placeholder* en la SPA: el odontólogo no puede configurar desde la interfaz los datos del consultorio, sus horarios, prestaciones, bloqueos ni parámetros de reserva, aunque el backend ya expone esos contratos (C-07, C-08, C-09). Sin esta pantalla, la puesta en marcha del consultorio (Flujo 2 de la KB) exige interactuar con la API a mano y no se puede obtener ni compartir el link público de reserva. Es el primer change de la Fase 6 y desbloquea la operación real del producto.

## What Changes

- Reemplazar el placeholder de la ruta privada `/configuracion` por la vista operativa de configuración, organizada en secciones: datos del consultorio y parámetros de reserva, horarios de atención, prestaciones y bloqueos.
- Consumir los endpoints ya existentes con un gateway tipado y decodificadores: `GET/PUT /api/v1/practice/settings`, `GET/PUT /api/v1/working-hours`, `GET/POST/PUT/DELETE /api/v1/services` y `GET/POST/PUT/DELETE /api/v1/schedule-blocks`.
- Formularios con validación local antes de enviar: tramos con fin posterior al inicio y sin solapamientos dentro del mismo día, duración de prestación > 0 y múltiplo de la granularidad vigente, rango de bloqueo válido y motivo obligatorio, y límites de los parámetros de reserva; los errores del servidor se mapean a mensajes por campo o alerta, nunca se muestran `title`/`detail` crudos.
- Baja lógica de prestaciones (*desactivar*, sin borrado físico) con posibilidad de reactivar; estados vacíos y de error por sección, sin perder el resto de la pantalla.
- Aviso de turnos afectados al crear/editar un bloqueo (campo `affectedAppointments` de la respuesta) y advertencia al modificar horarios de que los turnos confirmados no se cancelan automáticamente.
- Botón «Copiar link público de reserva» que compone `/reservar` del mismo origen, usa el portapapeles cuando está disponible y ofrece una alternativa visible cuando no lo está.
- Render seguro con `h()`/`textContent`, sin `innerHTML`; datos e instantes UTC mostrados en `America/Argentina/Buenos_Aires` mediante utilidades ya existentes.
- Mockup HTML aprobado (design system Revision 1, política `system-and-mockups`) de todas las secciones y sus estados vacíos/error en 390×844 y 1440×900 **antes** del apply.
- Tests Vitest: validaciones puras, gateway con API mockeada, render seguro y flujos de alta/edición/baja/copia; Strict TDD con ciclos RED → GREEN → TRIANGULATE → REFACTOR.

## Capabilities

### New Capabilities

- `settings-ui`: comportamiento observable de la pantalla de configuración del profesional —carga y estados de las secciones, validación de formularios, baja lógica de prestaciones, avisos de turnos afectados, copia del link público y render seguro—.

### Modified Capabilities

- `spa-routing`: se agrega el requisito de que `/configuracion` renderice la vista operativa de configuración (en lugar del placeholder actual) bajo el guard de sesión y el layout privado existentes.

## Impact

- **Frontend**: `frontend/src/app/routes.ts` (sustitución del placeholder, inyección del gateway), nuevo módulo `frontend/src/features/settings/` (gateway, decodificadores, mensajes, validación pura y vistas por sección), `frontend/src/styles/settings.css` con tokens del design system y tests Vitest colocated. Sin frameworks ni dependencias de runtime nuevas.
- **APIs consumidas (sin cambios de backend)**: `/api/v1/practice/settings`, `/api/v1/working-hours`, `/api/v1/services`, `/api/v1/schedule-blocks`. CSRF de C-05/C-04 ya resuelto por `cookieCsrfTransport`; sesión vencida gestionada por `onUnauthorized` de `main.ts`.
- **Diseño**: `design-system/tokens.css`, `brand.js` y componentes de `design-system/components.html` (Revision 1 aprobada); mockups en `openspec/changes/c-19-settings-ui/mockups/` (fase posterior).
- **Dependencias**: C-06 `login-ui`, C-07 `services-catalog`, C-08 `working-hours-booking-settings` y C-09 `schedule-blocks`, todas archivadas. No bloquea ni modifica ningún change en curso.
- **Sin impacto en datos clínicos**: no se accede a historia clínica ni se escriben datos de pacientes; el motivo del bloqueo se trata como texto del usuario y nunca se loguea.
