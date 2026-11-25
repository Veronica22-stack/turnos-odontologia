# Tasks: `frontend-ui-fixes-2`

> Change documentado después de implementado: todas las tareas están hechas y verificadas (tests Vitest, `npm run typecheck` OK y verificación en navegador). No toca `backend/`. Sin dependencias nuevas.

## 1. Diálogo «Nuevo turno» (spec `agenda`)

- [x] 1.1 Tests en `agenda-view.test.ts`: al cerrar el formulario «Nuevo turno» se quita del documento y abrirlo dos veces deja un único formulario; verificado con Vitest
- [x] 1.2 `agenda-view.ts`: el formulario se elimina en el evento `close` y una nueva apertura reemplaza al anterior; tests en verde
- [x] 1.3 `agenda.css`: `display: grid` de los diálogos sin cabecera solo con `[open]` y `dialog:not([open]) { display: none }`; verificado en navegador (sin restos al pie de otras vistas)

## 2. Baja de paciente libera turnos (spec `patients-ui`)

- [x] 2.1 Tests en `release-appointments.test.ts`: consulta en tramos de 90 días hasta 365, filtra por paciente, `CONFIRMED` y futuros, deduplica y cancela cada uno; un listado fallido no cancela nada; las cancelaciones fallidas se informan; verificado con Vitest
- [x] 2.2 `release-appointments.ts` con los endpoints existentes de la agenda; tests en verde
- [x] 2.3 Tests en `patients-view.test.ts` y `patients-integration.test.ts`: la confirmación advierte la cancelación; la baja libera antes del `DELETE` y el éxito informa N turnos liberados; un fallo del listado no da de baja y ofrece «Reintentar»; una cancelación parcial muestra advertencia; verificado con Vitest
- [x] 2.4 `patients-view.ts`, `patients-state.ts`, `patients-messages.ts` (`releaseError`, `deactivatedCopy`) y `routes.ts` (inyecta el gateway de agenda); tests en verde

## 3. Índice «En esta página» (spec `settings-ui`)

- [x] 3.1 Tests en `section-spy.test.ts`: `pickActiveSection` elige la última sección intersectada, la última al final de la página y conserva la actual sin datos; verificado con Vitest
- [x] 3.2 `section-spy.ts` con cálculo por posición en cada scroll (`sectionsCrossing`) más `IntersectionObserver`, desconexión al salir la vista del documento; tests en verde
- [x] 3.3 Tests en `settings-view.test.ts`: el clic en un enlace marca solo ese enlace, llama a `scrollIntoView` y previene la navegación; verificado con Vitest
- [x] 3.4 `settings-view.ts`: manejador de clic con `preventDefault` y scroll-spy cableado a `aria-current`; tests en verde

## 4. Verificación

- [x] 4.1 Tests de frontend y `npm run typecheck` en verde; recorrido en navegador de `/agenda`, `/pacientes` (baja con turnos) y `/configuracion` (índice)

## Workflow follow-up

- Archivar el change con `openspec archive frontend-ui-fixes-2` antes del commit.
