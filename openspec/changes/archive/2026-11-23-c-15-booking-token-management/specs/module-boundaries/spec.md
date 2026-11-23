# Spec Delta

## ADDED Requirements

### Requirement: Reservations module can write audit events
Se habilita la arista `reservas → auditoria`, exclusivamente hacia `auditoria.api`, para que el módulo de reservas registre `BOOKING_TOKEN_USE` al usar un token. La arista es de ida: `auditoria` NO SHALL depender de `reservas`, directa ni transitivamente. El resto del grafo y las reglas de acceso por capa `api` se mantienen.

#### Scenario: Reserva pública registra auditoría
- **GIVEN** una clase de `reservas.application` que usa `auditoria.api.AuditLogger`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Solo la api de auditoría
- **GIVEN** una clase de `reservas` que referencia `auditoria.domain`, `auditoria.application` o `auditoria.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Auditoría sigue aislada
- **GIVEN** una clase de `auditoria` que usa un tipo de `reservas.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla y no se introduce ningún ciclo
