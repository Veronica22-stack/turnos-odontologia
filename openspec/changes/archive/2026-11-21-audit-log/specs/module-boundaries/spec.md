# Spec Delta

## ADDED Requirements

### Requirement: Auditoría aislada de los módulos de negocio
El módulo `auditoria` MUST NOT depender, directa ni transitivamente, de `agenda`, `reservas`, `pacientes`, `historiaclinica` ni `notificaciones` en ninguna de sus capas. Sus únicas dependencias hacia otros módulos SHALL ser `shared` y su propio módulo. La verificación de arquitectura SHALL hacer fallar el build ante cualquier violación, nombrando la clase de origen y la de destino.

#### Scenario: Auditoría referencia a pacientes
- **GIVEN** una clase de `auditoria.application` que usa un tipo de `pacientes.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Auditoría referencia a historia clínica desde el dominio
- **GIVEN** una clase de `auditoria.domain` que usa un tipo de `historiaclinica.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: Auditoría depende solo de shared
- **GIVEN** una clase de `auditoria.infrastructure` que usa únicamente tipos de `shared` y de su propio módulo
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

### Requirement: Los módulos de negocio solo reportan por el puerto público
Los módulos de negocio SHALL informar eventos de auditoría únicamente a través de la interfaz pública del módulo `auditoria`. Los tipos de persistencia, entidades y adaptadores de auditoría MUST NOT ser referenciados desde fuera de ese módulo.

#### Scenario: Reporte desde un módulo de negocio
- **GIVEN** un caso de uso de `historiaclinica` que informa un evento mediante el puerto público de auditoría
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Uso de un adaptador desde otro módulo
- **GIVEN** una clase de `pacientes.application` que usa un tipo de `auditoria.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino
