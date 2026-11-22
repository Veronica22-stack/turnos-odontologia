# Spec Delta

## MODIFIED Requirements

### Requirement: Grafo de dependencias permitido
Las dependencias entre módulos de negocio SHALL limitarse a: `reservas` → `agenda`, `pacientes`, `notificaciones`; `agenda` → `pacientes`; `historiaclinica` → `pacientes`, `auditoria`; `pacientes` → `auditoria`; `auth` → `auditoria`. `auth` y `auditoria` MUST NOT depender de módulos de negocio (`agenda`, `reservas`, `pacientes`, `historiaclinica`, `notificaciones`), directa ni transitivamente. No se permiten ciclos.

#### Scenario: Dependencia no declarada
- **GIVEN** una clase de `agenda` que usa un tipo de `reservas.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: agenda consulta pacientes por su api
- **GIVEN** una clase de `agenda.application` o `agenda.infrastructure` que usa `pacientes.api.PatientLookup`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: agenda no accede a la infraestructura de pacientes
- **GIVEN** una clase de `agenda` que usa un tipo de `pacientes.application`, `pacientes.domain` o `pacientes.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Ciclo agenda pacientes prohibido
- **GIVEN** una clase de `pacientes` que usa un tipo de `agenda.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: auditoria independiente
- **GIVEN** una clase de `auditoria` que usa un tipo de `pacientes.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: auth registra auditoría
- **GIVEN** una clase de `auth.application` que usa `auditoria.api.AuditLogger`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: pacientes registra auditoría
- **GIVEN** una clase de `pacientes.application` que usa `auditoria.api.AuditLogger`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: auth sin dependencias de negocio
- **GIVEN** una clase de `auth` que usa un tipo de `pacientes.api` o cualquier otro módulo de negocio
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: auth sin dependencia transitiva de negocio
- **GIVEN** una clase de `auth` que usa `auditoria.api`, y una clase de `auditoria` que usa `pacientes.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla por la arista `auditoria → pacientes`, no por la arista `auth → auditoria`

#### Scenario: shared accesible desde auth
- **GIVEN** una clase de `auth.api` que usa el limitador de tasa de `shared.api` o el bean `java.time.Clock` provisto por `shared.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

### Requirement: Contratos públicos de agenda para bloqueos
Los contratos para consultar rangos bloqueados (`BlockedRanges`) y turnos afectados por un bloqueo (`AffectedAppointments`), junto con sus tipos de resultado, SHALL publicarse en `agenda.api` y SHALL exponer únicamente tipos del JDK o del dominio, nunca entidades JPA, `Pageable`, `Specification`, repositorios ni tipos de `agenda.infrastructure`. Los consumidores de otros módulos SHALL depender solo de esos contratos públicos. `agenda` MUST NOT depender de `reservas` ni de otros módulos de negocio salvo `pacientes.api`, y la capa `application` MUST NOT depender de `api` ni de `infrastructure`.

#### Scenario: Consumo desde otro módulo por la api de agenda
- **GIVEN** una clase de `reservas.application` que usa `BlockedRanges` de `agenda.api` con instantes y UUID
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Dependencia a infraestructura de agenda
- **GIVEN** una clase de otro módulo que usa un repositorio o entidad de `agenda.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Dependencia prohibida desde agenda
- **GIVEN** una clase de `agenda` que depende de `reservas.api` o de cualquier otro módulo de negocio distinto de `pacientes.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: Contrato sin tipos de persistencia
- **GIVEN** una clase que implementa o consume `BlockedRanges` o `AffectedAppointments`
- **WHEN** se inspeccionan las firmas de esos contratos y se ejecuta la verificación de arquitectura
- **THEN** no aparecen tipos de `jakarta.persistence`, Spring Data ni `agenda.infrastructure`, y la verificación pasa

## ADDED Requirements

### Requirement: Contratos públicos de turnos en agenda.api
El contrato de operaciones de turno para otros módulos (que en este change expone solo la reprogramación) y los eventos de turno confirmado, cancelado y reprogramado SHALL publicarse en `agenda.api` con tipos del JDK o records propios, sin entidades JPA, repositorios, tipos de Spring Data ni datos personales del paciente. El alta y la cancelación de turnos MUST NOT exponerse en `agenda.api`; SHALL permanecer como operaciones internas de `agenda.application`, inaccesibles para otros módulos.

#### Scenario: Consumo de la reprogramación desde reservas
- **GIVEN** una clase de `reservas.application` que usa la operación de reprogramación del contrato de turnos de `agenda.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Alta o cancelación desde otro módulo
- **GIVEN** una clase de `reservas` que usa el servicio de aplicación de turnos de `agenda.application` para crear o cancelar un turno
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Eventos con datos mínimos
- **GIVEN** los eventos de turno publicados en `agenda.api`
- **WHEN** se inspeccionan sus componentes en la verificación de contratos de `agenda.api`
- **THEN** solo contienen identificadores, instantes y valores enumerados, sin notas ni datos personales

#### Scenario: Contrato o evento con tipos de persistencia
- **GIVEN** un contrato o evento de turno en `agenda.api` cuya firma expone una entidad JPA o un tipo de `agenda.infrastructure`
- **WHEN** se ejecuta la verificación de contratos de `agenda.api`
- **THEN** la verificación falla nombrando el tipo prohibido
