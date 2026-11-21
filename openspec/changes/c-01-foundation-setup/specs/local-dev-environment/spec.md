# Spec Delta: local-dev-environment

## Purpose

Permite que cualquier integrante ejecute la aplicación en su máquina con PostgreSQL instalado de forma nativa, cree la base y los roles con un script versionado sin credenciales, corra los tests de integración sin tener una base instalada, y use la SPA contra la API en desarrollo sin exponer servicios fuera de su máquina.

## ADDED Requirements

### Requirement: Base y roles creados por un script versionado
El repositorio SHALL incluir `infra/postgres/init/01-roles.sql`, un script SQL sin credenciales que, ejecutado como superusuario con psql (o con la herramienta PSQL de pgAdmin) y recibiendo como variables el nombre de la base y los usuarios y contraseñas de los roles, crea el rol de migración, el rol de aplicación y la base de la aplicación con dueño el rol de migración. Las credenciales MUST provenir de variables definidas por cada integrante y MUST NOT quedar escritas en archivos versionados.

#### Scenario: Primera ejecución sobre un PostgreSQL recién instalado
- **GIVEN** un PostgreSQL 17 instalado localmente y las variables `DB_NAME`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD`, `DB_USER` y `DB_PASSWORD` definidas
- **WHEN** se ejecuta el script como superusuario siguiendo el README
- **THEN** existen los roles indicados por `DB_MIGRATION_USER` y `DB_USER`, la base indicada por `DB_NAME` pertenece al rol de migración, y el rol de aplicación puede conectarse pero no crear objetos en el esquema `public`

#### Scenario: Falta una variable del script
- **GIVEN** no se definió la contraseña del rol de aplicación
- **WHEN** se ejecuta el script como indica el README
- **THEN** el script se detiene con error antes de crear ningún rol ni base

#### Scenario: Re-ejecución del script
- **GIVEN** el script ya se ejecutó una vez sobre la misma instancia
- **WHEN** se ejecuta de nuevo con las mismas variables (o con contraseñas nuevas)
- **THEN** termina sin errores, no duplica roles ni bases, conserva los datos existentes y deja vigentes las contraseñas indicadas

### Requirement: Aplicación local configurada por variables de entorno
Con la base y los roles creados y las variables de `.env.example` definidas en el entorno del proceso (consola o configuración de ejecución del IDE), la aplicación SHALL arrancar con el perfil `dev`, aplicar las migraciones con el rol de migración y atender en `http://localhost:8080`.

#### Scenario: Arranque local completo
- **GIVEN** PostgreSQL local con el script de roles aplicado y las variables definidas como indica el README
- **WHEN** se ejecuta `./gradlew bootRun` desde `backend/`
- **THEN** `GET http://localhost:8080/actuator/health` responde `200` con `status` igual a `UP`

#### Scenario: Credenciales de base incorrectas
- **GIVEN** `DB_PASSWORD` no coincide con la contraseña del rol de aplicación
- **WHEN** se inicia la aplicación
- **THEN** el arranque falla con un error de autenticación que nombra el usuario de base y no muestra ninguna contraseña

### Requirement: Tests de integración sin base instalada
Los tests de integración SHALL ejecutarse contra un PostgreSQL real temporal que el propio proceso de tests inicia y destruye, aplicando el mismo script de roles, de modo que `./gradlew test` y `./gradlew integrationTest` funcionen en cualquier máquina con JDK y en CI sin instalar PostgreSQL. El sistema MUST NOT usar una base en memoria distinta de PostgreSQL.

#### Scenario: Máquina sin PostgreSQL instalado
- **GIVEN** una máquina con JDK y sin PostgreSQL instalado ni en ejecución
- **WHEN** se ejecuta `./gradlew integrationTest`
- **THEN** los tests de integración se ejecutan contra un PostgreSQL 17 temporal y terminan sin errores

#### Scenario: Script de roles inválido
- **GIVEN** el script de roles contiene una sentencia SQL inválida
- **WHEN** se ejecuta `./gradlew integrationTest`
- **THEN** los tests de integración fallan indicando el error del script

### Requirement: Servicios solo en la máquina local
El servidor de desarrollo del frontend y la documentación de instalación SHALL dejar los servicios escuchando solo en la máquina local: Vite en `localhost` y PostgreSQL instalado sin aceptar conexiones de otras máquinas de la red.

#### Scenario: Servidor de desarrollo local
- **WHEN** se inicia el servidor de desarrollo del frontend con la configuración versionada
- **THEN** escucha en `localhost` y no en todas las interfaces de red

### Requirement: SPA y API en el mismo origen en desarrollo
El servidor de desarrollo del frontend SHALL reenviar las solicitudes a `/api` y `/actuator` hacia el backend local, de modo que el navegador las vea como del mismo origen y no se requiera CORS.

#### Scenario: Salud vía servidor de desarrollo
- **GIVEN** el backend y el servidor de desarrollo del frontend en ejecución
- **WHEN** el navegador pide `/actuator/health` al servidor de desarrollo del frontend
- **THEN** recibe la respuesta del backend
