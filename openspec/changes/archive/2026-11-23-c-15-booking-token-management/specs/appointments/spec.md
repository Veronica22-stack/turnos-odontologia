# Spec Delta

## ADDED Requirements

### Requirement: Public cancellation through the shared port
El módulo `agenda` SHALL exponer en su capa `api` una operación de cancelación para consumidores de otros módulos que delegue en el mismo servicio interno de turnos con `cancelledBy = PATIENT`. La operación SHALL publicar el evento de cancelación con ese valor para que el outbox envíe la cancelación al paciente y el aviso al odontólogo. NO SHALL existir un segundo camino de escritura sobre `appointment`.

#### Scenario: Cancelación por el paciente
- **GIVEN** un turno `CONFIRMED` futuro
- **WHEN** un consumidor externo invoca la cancelación pública
- **THEN** el turno queda `CANCELLED` con `cancelled_by = PATIENT` y se publica `AppointmentCancelled` con ese mismo valor

#### Scenario: Sin duplicar la máquina de estados
- **GIVEN** un turno ya cancelado
- **WHEN** se invoca la cancelación pública
- **THEN** se devuelve el mismo conflicto de transición de estado que la cancelación interna y no se publica otro evento
