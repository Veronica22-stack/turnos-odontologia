# Spec Delta

## ADDED Requirements

### Requirement: Public booking module access
El módulo `reservas` SHALL acceder a `agenda.api`, `pacientes.api` y `notificaciones.api` para crear turnos, resolver pacientes y proveer el enlace de gestión. El acceso SHALL limitarse a esas capas `api`; NO SHALL acceder a `domain`, `application` ni `infrastructure` de otros módulos.

#### Scenario: Reserva usa contratos públicos
- **GIVEN** una clase de `reservas.application` o `reservas.infrastructure` que crea un turno y resuelve un paciente
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa porque solo depende de `agenda.api` y `pacientes.api`

#### Scenario: Sin acceso a capas internas
- **GIVEN** una clase de `reservas` que referencia `agenda.domain`, `agenda.infrastructure`, `pacientes.domain` o `pacientes.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Proveedor de enlaces implementado en reservas
- **GIVEN** la implementación de `notificaciones.api.ManagementLinkProvider` en `reservas.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa y no aparece ninguna dependencia de `notificaciones` fuera de su capa `api`
