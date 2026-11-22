# Spec Delta

## MODIFIED Requirements

### Requirement: Grafo de dependencias permitido
Las dependencias entre módulos de negocio SHALL limitarse a: `reservas` → `agenda`, `pacientes`, `notificaciones`; `historiaclinica` → `pacientes`, `auditoria`; `pacientes` → `auditoria`; `auth` → `auditoria`. `auth` y `auditoria` MUST NOT depender de módulos de negocio (`agenda`, `reservas`, `pacientes`, `historiaclinica`, `notificaciones`), directa ni transitivamente. No se permiten ciclos.

#### Scenario: Dependencia no declarada
- **GIVEN** una clase de `agenda` que usa un tipo de `reservas.api`
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
