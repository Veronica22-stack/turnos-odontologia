# Propuesta: C-01 `foundation-setup`

> ID del roadmap: `C-01` (ver `CHANGES.md`). El CLI de OpenSpec exige nombres en minúscula, por eso el change se llama `c-01-foundation-setup`.

## Why

El repositorio tiene la base de conocimiento, el roadmap y el design system aprobados, pero todavía no existe código: no hay backend, frontend, base de datos ni tooling de tests. Los 27 changes siguientes (empezando por `C-02 core-models-shared`, en el camino crítico) necesitan un esqueleto ejecutable y verificable donde aplicar Strict TDD, con las fronteras de módulos, el manejo de credenciales y la estrategia de roles de base de datos ya resueltos para no retrabajarlos en cada change.

## What Changes

- **Monorepo** con `backend/` (Spring Boot, Gradle Kotlin DSL, Java 21, paquete base `…consultorio`) y `frontend/` (Vite + TypeScript estricto, sin framework y sin dependencias de runtime).
- **Estructura modular del backend**: módulos `agenda`, `reservas`, `pacientes`, `historiaclinica`, `notificaciones`, `auth`, `auditoria`, `shared`, cada uno con capas `api` / `application` / `domain` / `infrastructure`, y un test de arquitectura que hace fallar el build si se viola el grafo de dependencias de KB 08.
- **Base de datos**: PostgreSQL con Flyway; dos roles (`migrator` con DDL, `app` solo DML, sin DDL) y privilegios por defecto que permiten revocar UPDATE/DELETE en tablas inmutables en changes posteriores. Migración `V1` base (extensión `btree_gist` y privilegios), verificada con Testcontainers.
- **Configuración**: `application.yml` + `application-dev.yml` que leen todo dato sensible desde variables de entorno (sin valores por defecto para secretos), `.env.example` con todas las variables de KB 08 §Variables de entorno, almacenamiento en UTC.
- **Seguridad mínima deny-by-default**: solo `GET /actuator/health` es público; cualquier otra ruta responde 401 hasta que `C-04` agregue autenticación.
- **Entorno local**: `docker-compose.yml` con app + PostgreSQL + Mailpit, puertos ligados a `127.0.0.1`, roles de BD creados por un script de inicialización.
- **Tooling de tests**: JUnit 5 + Testcontainers + ArchUnit + JaCoCo (backend), Vitest + jsdom (frontend); `./gradlew test`, `./gradlew integrationTest` y `npm test` verdes con al menos un smoke test por lado.
- **SPA mínima**: `index.html` + `main.ts` que monta un contenedor raíz vacío (sin UI visible), y proxy de Vite hacia el backend en desarrollo.
- Se **extienden** (no se reemplazan) `.gitignore` y `.gitattributes` existentes.

## Capabilities

### New Capabilities

- `platform-health`: endpoint de salud de la aplicación que refleja la disponibilidad de la base sin exponer detalles internos.
- `api-access-policy`: política de acceso HTTP deny-by-default; qué rutas son públicas y cómo responde el sistema a las no autorizadas.
- `database-migrations`: evolución versionada del esquema, separación de privilegios entre el rol de migración y el de la aplicación, y extensiones requeridas.
- `module-boundaries`: reglas verificables de dependencia entre los módulos del monolito y entre capas dentro de cada módulo.
- `runtime-configuration`: origen de la configuración (variables de entorno), arranque que falla ante configuración faltante y manejo de fechas en UTC.
- `local-dev-environment`: entorno local reproducible (app, base de datos, servidor de email de prueba) y comunicación SPA ↔ API en desarrollo.
- `spa-shell`: arranque de la SPA y montaje en su contenedor raíz.

### Modified Capabilities

Ninguna (no existen specs previas en `openspec/specs/`).

## Impact

- **Código nuevo**: `backend/` (proyecto Gradle completo con wrapper), `frontend/` (proyecto npm), `docker-compose.yml`, `.env.example`, `infra/postgres/init/` (script de roles), `backend/Dockerfile`.
- **Archivos existentes**: `.gitignore` y `.gitattributes` se extienden.
- **Dependencias nuevas**: listadas una por una en `design.md` §Dependencias para aprobación explícita (regla dura 12). El frontend no agrega dependencias de runtime; solo herramientas de desarrollo.
- **Requisitos de máquina**: Docker (para compose y Testcontainers), JDK 21 (o aprovisionamiento automático por toolchain de Gradle), Node.js LTS.
- **Sin cambios de API de negocio**: la única ruta expuesta es `/actuator/health`.
- **Fuera de alcance**: tablas de negocio (`practice`, `app_user` → `C-02`), `audit_log` (`C-03`), autenticación (`C-04`), envío de emails, empaquetado de la SPA dentro de la imagen de producción, CI remoto.
