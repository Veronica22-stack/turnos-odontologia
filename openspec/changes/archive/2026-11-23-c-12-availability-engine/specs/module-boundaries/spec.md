# Spec Delta

## ADDED Requirements

### Requirement: Contratos de lectura para el motor de disponibilidad
Los contratos de solo lectura que alimentan el motor de disponibilidad SHALL publicarse en `agenda.api` (`WorkingHoursLookup`, `ServiceAvailabilityLookup`) y en `shared.api` (`PracticeBookingSettingsLookup`), con firmas compuestas solo por tipos del JDK y records propios de esas capas `api`. Sus implementaciones SHALL residir en la `infrastructure` de su módulo y SHALL delegar en los servicios de aplicación existentes; `reservas.application` SHALL consumirlos como cualquier dependencia de `agenda.api` / `shared.api`. Ninguno de estos contratos SHALL exponer entidades JPA, repositorios, `Pageable`, `Specification` ni tipos de infraestructura.

#### Scenario: Consumo desde reservas.application
- **GIVEN** una clase de `reservas.application` que usa `agenda.api.WorkingHoursLookup` y `shared.api.PracticeBookingSettingsLookup`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa y el grafo `reservas → agenda, shared` se mantiene sin ciclos

#### Scenario: Contrato sin tipos de persistencia
- **GIVEN** las firmas de los tres contratos de lectura y sus records de resultado
- **WHEN** se inspeccionan en la verificación de contratos
- **THEN** no aparecen `jakarta.persistence`, Spring Data, `agenda.infrastructure` ni `shared.infrastructure`

#### Scenario: Dependencia prohibida en sentido inverso
- **GIVEN** una clase de `agenda` que usa un tipo de `reservas.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando origen y destino

### Requirement: Contrato público de disponibilidad en reservas.api
El módulo `reservas` SHALL publicar en su capa `api` un contrato de consulta de disponibilidad con records propios y tipos del JDK (identificadores, fecha, zona, instantes y estado), sin datos personales, clínicos ni tipos de framework. La capa `application` SHALL usar el caso de uso subyacente sin depender de `reservas.api` ni de `reservas.infrastructure`; los controladores SHALL delegar en ese mismo caso de uso. El dominio de `reservas` SHALL recibir los datos de ocupación ya mapeados a tipos propios, sin depender de `agenda.api`, `shared.api` ni de Spring.

#### Scenario: Consumo del contrato por la reserva pública
- **GIVEN** un caso de uso de `reservas` que necesita validar un slot durante la reserva (C-14)
- **WHEN** consulta la disponibilidad
- **THEN** lo hace mediante el mismo caso de uso que implementa el contrato público, sin referenciar el controller ni la infraestructura

#### Scenario: Dominio puro sin puertos externos
- **GIVEN** una clase de `reservas.domain` que calcula slots
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** no depende de `agenda.api`, `shared.api`, Spring ni `java.sql`, y la verificación pasa

#### Scenario: Contrato con datos personales o persistencia
- **GIVEN** un contrato de `reservas.api` de disponibilidad que exponga nombres, DNI, email, teléfono, entidades JPA o tipos de `reservas.infrastructure`
- **WHEN** se ejecuta la verificación de contratos y de arquitectura
- **THEN** la verificación falla nombrando el tipo prohibido
