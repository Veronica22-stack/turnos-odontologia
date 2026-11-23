# Proposal: C-15 `service-id-in-booking-view`

## Why

C-25 (gestión del turno por link) necesita el `serviceId` de la prestación en la respuesta de `GET /api/v1/public/bookings/{token}` para consultar disponibilidad al reprogramar. Sin él, resolvía la prestación por nombre exacto y único, un workaround frágil ante un cambio de nombre en el catálogo. El pedido llegó como P1 en `design.md` §Pedidos a C-15 de C-25; el equipo acordó que C-25 funciona igual con o sin el campo (su gateway ya lo decodifica como opcional).

## What Changes

- **Cambio aditivo al contrato**: `BookingView` y `BookingDetails` incorporan `UUID serviceId`.
- **Origen del dato**: `BookingManagementUseCase.toDetails` lo resuelve con el puerto ya existente `AppointmentServiceLookup.findServiceId(appointmentId)`, sin búsqueda por nombre.
- **Propagación**: `BookingManagementController.toView` lo incluye en las respuestas de `GET`, `DELETE` y `POST .../reschedule`.
- **Especificación**: la spec principal `booking-token-management` ya fue actualizada con el campo; no hay deltas pendientes (`skip_specs: true`).
- **Tests**: aserciones de `$.serviceId` en los IT del token y del caso de uso.

### Fuera de alcance

- Cambios en C-25 (frontend): su gateway ya acepta `serviceId` opcional.
- Rotación del token, nuevos endpoints o cambios de errores.
- Cambios en la lógica de disponibilidad o en el catálogo de prestaciones.

## Capabilities

### New Capabilities
- Ninguna.

### Modified Capabilities
- Ninguna pendiente: el cambio de requisito en `booking-token-management` ya está sincronizado en `openspec/specs/`; este change es el registro retroactivo del pedido P1.

## Impact

- **Backend**: 4 archivos de producción y 2 de test en `reservas`.
- **API**: campo aditivo `serviceId` (UUID opaco, ya público en `GET /api/v1/public/services`); sin datos personales nuevos.
- **Especificación**: `openspec/specs/booking-token-management/spec.md` actualizada en la entrega anterior.
- **Git**: sin commits ni push; `HEAD` sin cambios.
