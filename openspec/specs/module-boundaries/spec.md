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
Las dependencias entre módulos de negocio SHALL limitarse a: `reservas` → `agenda`, `pacientes`, `notificaciones`; `historiaclinica` → `pacientes`, `auditoria`. `auth` y `auditoria` MUST NOT depender de módulos de negocio. No se permiten ciclos.

#### Scenario: Dependencia no declarada
- **GIVEN** una clase de `agenda` que usa un tipo de `reservas.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

#### Scenario: auditoria independiente
- **GIVEN** una clase de `auditoria` que usa un tipo de `pacientes.api`
- **WHEN** se ejecuta la verificación de arquitectura
- **THEN** la verificación falla

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
