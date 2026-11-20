# Spec Delta: local-dev-environment

## Purpose

Ofrece un entorno local reproducible con un solo comando (aplicación, PostgreSQL y servidor de email de prueba) y la conexión entre la SPA y la API en desarrollo, sin exponer servicios fuera de la máquina del desarrollador.

## ADDED Requirements

### Requirement: Entorno con un comando
Con un archivo `.env` creado a partir de `.env.example`, `docker compose up` SHALL levantar la aplicación, PostgreSQL y un servidor SMTP de prueba con interfaz web, y la aplicación SHALL esperar a que la base esté lista antes de arrancar.

#### Scenario: Arranque completo
- **GIVEN** Docker disponible y un `.env` completo
- **WHEN** se ejecuta `docker compose up`
- **THEN** `GET http://127.0.0.1:8080/actuator/health` responde `UP` y la interfaz del servidor de email de prueba responde en `http://127.0.0.1:8025`

#### Scenario: Falta el archivo .env
- **GIVEN** no existe `.env`
- **WHEN** se ejecuta `docker compose up`
- **THEN** el entorno no arranca y el error indica que falta configuración

### Requirement: Roles de base creados al inicializar
Al crear el volumen de datos por primera vez, el entorno SHALL crear el rol de migración y el rol de aplicación con las credenciales tomadas de las variables de entorno, sin credenciales escritas en archivos versionados.

#### Scenario: Primer arranque
- **WHEN** PostgreSQL inicializa un volumen vacío
- **THEN** existen los roles indicados por `DB_MIGRATION_USER` y `DB_USER`, y la base de la aplicación pertenece al rol de migración

### Requirement: Servicios solo en la máquina local
Los puertos publicados por el entorno SHALL ligarse a `127.0.0.1`, de modo que la base y los servicios no sean accesibles desde otras máquinas de la red.

#### Scenario: Puerto de base no expuesto a la red
- **WHEN** se inspeccionan los puertos publicados
- **THEN** todos están ligados a `127.0.0.1`

### Requirement: SPA y API en el mismo origen en desarrollo
El servidor de desarrollo del frontend SHALL reenviar las solicitudes a `/api` y `/actuator` hacia el backend local, de modo que el navegador las vea como del mismo origen y no se requiera CORS.

#### Scenario: Salud vía servidor de desarrollo
- **GIVEN** el backend y el servidor de desarrollo del frontend en ejecución
- **WHEN** el navegador pide `/actuator/health` al servidor de desarrollo del frontend
- **THEN** recibe la respuesta del backend
