# Spec Delta

## ADDED Requirements

### Requirement: Contratos públicos de agenda para bloqueos
Los contratos para consultar rangos bloqueados (`BlockedRanges`) y turnos afectados por un bloqueo (`AffectedAppointments`), junto con sus tipos de resultado, SHALL publicarse en `agenda.api` y SHALL exponer únicamente tipos del JDK o del dominio, nunca entidades JPA, `Pageable`, `Specification`, repositorios ni tipos de `agenda.infrastructure`. Los consumidores de otros módulos SHALL depender solo de esos contratos públicos. `agenda` MUST NOT depender de `reservas` ni de otros módulos de negocio, y la capa `application` MUST NOT depender de `api` ni de `infrastructure`.

#### Scenario: Consumo desde otro módulo por la api de agenda
- **GIVEN** una clase de `reservas.application` que usa `BlockedRanges` de `agenda.api` con instantes y UUID
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Dependencia a infraestructura de agenda
- **GIVEN** una clase de otro módulo que usa un repositorio o entidad de `agenda.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Dependencia prohibida desde agenda
- **GIVEN** una clase de `agenda` que depende de `reservas.api` o de cualquier otro módulo de negocio
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: Contrato sin tipos de persistencia
- **GIVEN** una clase que implementa o consume `BlockedRanges` o `AffectedAppointments`
- **WHEN** se inspeccionan las firmas de esos contratos y se ejecuta la verificación de arquitectura
- **THEN** no aparecen tipos de `jakarta.persistence`, Spring Data ni `agenda.infrastructure`, y la verificación pasa
