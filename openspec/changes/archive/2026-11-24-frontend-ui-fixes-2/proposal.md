# Proposal: `frontend-ui-fixes-2`

## Why

Tras el change `frontend-ui-fixes` aparecieron tres defectos en el uso real de la SPA: la ventana «Nuevo turno» de la agenda quedaba visible al pie de otras páginas (regresión de los estilos de diálogos); dar de baja a un paciente dejaba sus turnos futuros confirmados ocupando horarios en la agenda y en la reserva pública; y el índice «En esta página» de Configuración marcaba siempre «Consultorio y reserva» como sección actual, sin importar dónde estuviera la persona. Este change documenta y cierra esos arreglos, ya implementados y verificados en el working tree.

Governance: **MEDIO** (lógica y presentación de frontend). No toca backend, API ni datos: la liberación de turnos usa los endpoints existentes de la agenda. Sin dependencias nuevas (regla dura 12).

## What Changes

- **Diálogo «Nuevo turno» (`/agenda`)**: un `<dialog>` cerrado vuelve a estar oculto (la regla `display: grid` de los diálogos sin cabecera ahora aplica solo con `[open]`, y `dialog:not([open])` queda con `display: none`). El formulario de alta se quita del documento al cerrarse y abrirlo otra vez reemplaza al anterior, de modo que no quedan restos en otras vistas.
- **Baja de paciente libera turnos (`/pacientes`)**: antes del `DELETE /api/v1/patients/{id}` la SPA consulta los turnos desde ahora hasta 365 días (en tramos de 90 días, porque el listado admite como máximo 93), filtra los del paciente que estén `CONFIRMED` y en el futuro, los deduplica y cancela cada uno con `POST /api/v1/appointments/{id}/cancel`. Si el listado falla no se cancela ni se da de baja nada (aviso con «Reintentar»); si alguna cancelación falla, la baja igual se completa y se avisa qué turnos quedaron sin liberar. El diálogo de confirmación advierte la cancelación y el mensaje de éxito informa cuántos turnos se liberaron.
- **Índice «En esta página» (`/configuracion`)**: al hacer clic en un enlace se marca como actual y se desplaza a la sección sin navegación del router (se evita el `popstate` del salto de ancla que re-renderizaba la vista); un scroll-spy (por posición en cada scroll, más `IntersectionObserver`) marca la sección visible al desplazarse y la última al llegar al final de la página.

Fuera de alcance: cancelación de turnos en el backend como parte de la baja (transacción única), notificación al paciente por los turnos cancelados más allá de lo que ya haga el endpoint de cancelación, cambios de backend.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities
- `patients-ui`: la baja lógica libera antes los turnos futuros confirmados del paciente.
- `settings-ui`: el índice de secciones marca la sección actual según el clic y el desplazamiento.
- `agenda`: el formulario de alta de turno cerrado no queda visible ni persiste en el documento.

## Impact

- **Código** (`frontend/`, ya implementado): `src/styles/agenda.css`, `src/features/agenda/agenda-view.ts`, `src/features/patients/{release-appointments,patients-view,patients-state,patients-messages}.ts`, `src/app/routes.ts`, `src/features/settings/{section-spy,settings-view}.ts`; tests Vitest correspondientes (`agenda-view.test.ts`, `release-appointments.test.ts`, `patients-view.test.ts`, `patients-integration.test.ts`, `section-spy.test.ts`, `settings-view.test.ts`).
- **Backend / API / datos**: sin cambios.
- **Dependencias**: ninguna nueva.
