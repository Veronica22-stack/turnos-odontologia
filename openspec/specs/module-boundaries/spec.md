# module-boundaries Specification

## Purpose
Hace verificables, en cada build, las fronteras del monolito modular (KB 08 §Módulos) para que el acoplamiento entre módulos y capas no crezca en silencio y el dominio siga siendo testeable sin framework.

## Requirements

### Requirement: Módulos y capas definidos
El backend SHALL organizarse en los módulos `agenda`, `reservas`, `pacientes`, `historiaclinica`, `notificaciones`, `auth`, `auditoria` y `shared`, cada uno con las capas `api`, `application`, `domain` e `infrastructure`.

#### Scenario: Estructura completa
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** existe cada combinación módulo/capa y no existen paquetes de primer nivel fuera de esa lista además de la clase de arranque

### Requirement: Acceso entre módulos solo por su api
Una clase de un módulo SHALL depender de otro módulo únicamente a través de la capa `api` de ese otro módulo. `shared` es accesible desde todos los módulos.

#### Scenario: Dependencia a la api de otro módulo
- **GIVEN** una clase de `reservas.application` que usa un tipo de `agenda.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

#### Scenario: Dependencia a la infraestructura de otro módulo
- **GIVEN** una clase de `reservas` que usa un tipo de `agenda.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

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

### Requirement: Dominio independiente del framework
La capa `domain` de cada módulo MUST NOT depender de las capas `api`, `application` ni `infrastructure`, ni de ningún paquete de Spring, ni de JDBC (`java.sql`). Las únicas dependencias de framework permitidas en `domain` son las anotaciones de mapeo de persistencia (`jakarta.persistence` y `org.hibernate.annotations`). La capa `application` MUST NOT depender de `api` ni de `infrastructure`.

#### Scenario: Dominio que usa infraestructura
- **GIVEN** una clase de `pacientes.domain` que usa un tipo de `pacientes.infrastructure`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: Dominio que usa Spring
- **GIVEN** una clase de `pacientes.domain` que usa un tipo de cualquier paquete `org.springframework`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla nombrando la clase de origen y la de destino

#### Scenario: Dominio con anotaciones de persistencia
- **GIVEN** una clase de `pacientes.domain` anotada con anotaciones de `jakarta.persistence` (p. ej. `Entity` o `MappedSuperclass`) y con un generador de `org.hibernate.annotations`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación pasa

### Requirement: Verificación en el build
La verificación de arquitectura SHALL ejecutarse como parte de la suite de tests unitarios, de modo que una violación haga fallar el build.

#### Scenario: Violación rompe el build
- **WHEN** existe una violación de cualquiera de las reglas anteriores y se ejecuta la suite unitaria
- **THEN** la suite termina con error

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

### Requirement: Aislamiento de la capa de autenticación
El módulo `auth` SHALL exponer su funcionamiento solo a través de `auth.api` y SHALL NOT filtrar detalles de sesión, credenciales o tokens hacia otros módulos. La configuración de seguridad HTTP SHALL residir en `auth.infrastructure` y MUST NOT crear dependencias de los módulos de negocio. Las pruebas de arquitectura SHALL verificar estas restricciones como parte de la suite unitaria.

#### Scenario: Seguridad confinada en auth
- **GIVEN** la cadena de filtros de Spring Security y la configuración de cookies
- **WHEN** se inspeccionan sus paquetes
- **THEN** todas pertenecen a `auth.infrastructure`

#### Scenario: Otro módulo consulta la sesión
- **GIVEN** una clase de `agenda` que necesita saber si hay una sesión autenticada
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla si depende de `auth.infrastructure`; debe usar solo contratos de `shared.api` o del contexto HTTP estándar

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
