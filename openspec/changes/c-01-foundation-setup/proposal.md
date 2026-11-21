# Propuesta: C-01 `foundation-setup`

> ID del roadmap: `C-01` (ver `CHANGES.md`). El CLI de OpenSpec exige nombres en minúscula, por eso el change se llama `c-01-foundation-setup`.
> Revisado el 2026-11-21 por decisión del usuario (DD-10): ejecución local con PostgreSQL instalado de forma nativa y tests con PostgreSQL embebido. Ver `design.md` §Historial de cambios.

## Why

El repositorio tiene la base de conocimiento, el roadmap y el design system aprobados, pero todavía no existe código: no hay backend, frontend, base de datos ni tooling de tests. Los 27 changes siguientes (empezando por `C-02 core-models-shared`, en el camino crítico) necesitan un esqueleto ejecutable y verificable donde aplicar Strict TDD, con las fronteras de módulos, el manejo de credenciales y la estrategia de roles de base de datos ya resueltos para no retrabajarlos en cada change. Varios integrantes del equipo trabajan solo con JDK, Node y PostgreSQL nativo, así que el entorno y los tests no deben requerir herramientas adicionales.

## What Changes

- **Monorepo** con `backend/` (Spring Boot, Gradle Kotlin DSL, Java 21, paquete base `…consultorio`) y `frontend/` (Vite + TypeScript estricto, sin framework y sin dependencias de runtime).
- **Estructura modular del backend**: módulos `agenda`, `reservas`, `pacientes`, `historiaclinica`, `notificaciones`, `auth`, `auditoria`, `shared`, cada uno con capas `api` / `application` / `domain` / `infrastructure`, y un test de arquitectura que hace fallar el build si se viola el grafo de dependencias de KB 08.
- **Base de datos**: PostgreSQL 17 con Flyway; dos roles (`migrator` con DDL, `app` solo DML, sin DDL) y privilegios por defecto que permiten revocar UPDATE/DELETE en tablas inmutables en changes posteriores. Migración `V1` base (extensión `btree_gist` y privilegios), verificada con PostgreSQL embebido.
- **Script de roles**: `infra/postgres/init/01-roles.sql`, SQL versionado sin credenciales e idempotente, que crea la base y los roles; se ejecuta una vez con psql o pgAdmin y es el mismo que usan los tests de integración.
- **Configuración**: `application.yml` + `application-dev.yml` que leen todo dato sensible desde variables de entorno (sin valores por defecto para secretos), `.env.example` con todas las variables de KB 08 §Variables de entorno, almacenamiento en UTC.
- **Seguridad mínima deny-by-default**: solo `GET /actuator/health` es público; cualquier otra ruta responde 401 hasta que `C-04` agregue autenticación.
- **Entorno local**: PostgreSQL instalado de forma nativa por cada integrante (instalador oficial de Windows + pgAdmin; pasos breves para macOS/Linux), backend con `./gradlew bootRun` o desde el IDE leyendo variables de entorno, y Vite con proxy al backend. Todo documentado en un `README.md`.
- **Tooling de tests**: JUnit 5 + PostgreSQL embebido (zonky) + ArchUnit + JaCoCo (backend), Vitest + jsdom (frontend); `./gradlew test`, `./gradlew integrationTest` y `npm test` verdes en cualquier máquina y en CI, sin instalar una base de datos.
- **SPA mínima**: `index.html` + `main.ts` que monta un nodo raíz vacío (sin UI visible), y proxy de Vite hacia el backend en desarrollo.
- Se **extienden** (no se reemplazan) `.gitignore` y `.gitattributes` existentes.

## Capabilities

### New Capabilities

- `platform-health`: endpoint de salud de la aplicación que refleja la disponibilidad de la base sin exponer detalles internos.
- `api-access-policy`: política de acceso HTTP deny-by-default; qué rutas son públicas y cómo responde el sistema a las no autorizadas.
- `database-migrations`: evolución versionada del esquema, separación de privilegios entre el rol de migración y el de la aplicación, y extensiones requeridas.
- `module-boundaries`: reglas verificables de dependencia entre los módulos del monolito y entre capas dentro de cada módulo.
- `runtime-configuration`: origen de la configuración (variables de entorno), arranque que falla ante configuración faltante y manejo de fechas en UTC.
- `local-dev-environment`: entorno local con PostgreSQL instalado de forma nativa, script de base y roles, tests de integración sin base instalada, y comunicación SPA ↔ API en desarrollo.
- `spa-shell`: arranque de la SPA y montaje en su elemento raíz.

### Modified Capabilities

Ninguna (no existen specs previas en `openspec/specs/`).

## Impact

- **Código nuevo**: `backend/` (proyecto Gradle completo con wrapper), `frontend/` (proyecto npm), `.env.example`, `infra/postgres/init/01-roles.sql`, `README.md` en la raíz.
- **Archivos existentes**: `.gitignore` y `.gitattributes` se extienden.
- **Borradores previos**: un apply interrumpido dejó borradores en `backend/`, `frontend/` e `infra/` basados en el plan anterior; la tarea 0.1 los revisa y adapta.
- **Dependencias nuevas**: listadas una por una en `design.md` §D13 para aprobación explícita (regla dura 12). El frontend no agrega dependencias de runtime; solo herramientas de desarrollo.
- **Requisitos de máquina**: JDK 21 (o aprovisionamiento automático por toolchain de Gradle), Node.js 22 LTS y, solo para ejecutar la app localmente, PostgreSQL 17 instalado. Los tests no requieren PostgreSQL instalado.
- **Sin cambios de API de negocio**: la única ruta expuesta es `/actuator/health`.
- **Fuera de alcance**: tablas de negocio (`practice`, `app_user` → `C-02`), `audit_log` (`C-03`), autenticación (`C-04`), envío de emails y su modo "al log" en desarrollo (`C-13`), estrategia de despliegue y empaquetado de la SPA en el artefacto de producción (`C-27`, TBD), CI remoto.
