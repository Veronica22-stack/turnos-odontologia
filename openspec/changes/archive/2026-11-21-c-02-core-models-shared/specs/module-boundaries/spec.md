# Spec Delta

## MODIFIED Requirements

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
