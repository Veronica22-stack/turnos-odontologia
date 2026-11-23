# Design: C-15 `service-id-in-booking-view`

## Context

C-15 fue implementado y archivado el 2026-11-23. `BookingView` exponía `bookingId`, `serviceName`, `practiceName`, instantes UTC, estado y banderas, pero no `serviceId`. C-25 resolvía la prestación por nombre exacto y único antes de consultar `GET /api/v1/public/availability?serviceId=&date=` al reprogramar (design D5 de C-25, pedido P1).

El repositorio ya tiene `agenda.api.AppointmentServiceLookup.findServiceId(UUID)`, usado por `BookingManagementUseCase.requireOfferedSlot`, así que no hace falta un puerto nuevo ni acceder a capas internas de `agenda`.

## Goals / Non-Goals

**Goals:** exponer el `serviceId` del turno en la vista por token de forma aditiva y sin costo extra; que C-25 pueda usar el id directo.

**Non-Goals:** cambiar el frontend, la disponibilidad, el token ni los códigos de error.

## Decisions

### D1 — Campo aditivo y origen canónico
Agregar `UUID serviceId` a `BookingView` (api) y `BookingDetails` (application). El valor se obtiene con `AppointmentServiceLookup.findServiceId(appointmentId)`; si la prestación ya no existe se devuelve `null` (JSON `null`), sin fallar la consulta. Es el mismo agregado que ya usa la validación de reprogramación.

### D2 — Sin búsqueda por nombre
El nombre de la prestación es información mutable del catálogo; el id es estable y ya es público en `GET /api/v1/public/services`. La resolución por nombre queda solo como fallback opcional de C-25, no como contrato del servidor.

### D3 — Especificación ya sincronizada
La spec principal `booking-token-management` ya incluye el campo desde la entrega anterior. El change declara `skip_specs: true` para no reintroducir un requirement duplicado; el archive no debe modificar specs.

### D4 — Verificación
Los IT del token y el test del caso de uso verifican `serviceId` en `GET`, `DELETE` y `POST .../reschedule`. La suite completa sigue en verde.

## Risks / Trade-offs

- **[Riesgo]** Un `serviceId` nulo si la prestación fue dada de baja: C-25 ya maneja ausencia de id con su resolución alternativa; no se emite un error nuevo.
- **[Riesgo]** Cambio de forma del record Java: solo hay un punto de construcción (el controller) y ningún test construye `BookingView` directamente; verificado con `./gradlew check`.

## Migration Plan

1. Desplegar código y spec juntos; el campo es aditivo, sin migración de base.
2. Rollback: quitar el campo del record y del controller; la spec principal volvería a la versión anterior (no se hace en este change).

## Specs

Sin deltas (`skip_specs: true`). La spec vigente es `openspec/specs/booking-token-management/spec.md`.
