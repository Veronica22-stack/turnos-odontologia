# Tasks: C-15 `service-id-in-booking-view`

> Change retroactivo: el código, los tests y la spec principal ya estaban aplicados cuando se creó este artefacto. Todas las tareas se marcan `[x]` porque su resultado existe y fue verificado con `./gradlew check`.

- [x] 1. Agregar `UUID serviceId` a `reservas.api.BookingView` y `reservas.application.BookingDetails`.
- [x] 2. Resolver el valor en `BookingManagementUseCase.toDetails` con `AppointmentServiceLookup.findServiceId(...)` (sin búsqueda por nombre).
- [x] 3. Propagarlo en `BookingManagementController.toView` para `GET`, `DELETE` y `POST .../reschedule`.
- [x] 4. Actualizar `openspec/specs/booking-token-management/spec.md` con el campo en la vista acotada (ya aplicado; `skip_specs: true`).
- [x] 5. Verificar `$.serviceId` en `BookingManagementEndpointsIT` y `BookingManagementUseCaseTest`.
- [x] 6. Ejecutar `./gradlew check`: 787 unitarios + 542 IT, 0 fallos.
- [x] 7. Actualizar `docs/qa/matriz-de-tests.md` y el `verify-report.md` archivado de C-15.
- [x] 8. Sin commits ni push.
