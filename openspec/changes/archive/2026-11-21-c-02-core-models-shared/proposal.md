# Proposal: C-02 `core-models-shared`

## Why

Todo change posterior (auditoría, auth, agenda, pacientes, HC) referencia al consultorio (`practice_id`), al usuario interno (`app_user`), al reloj, a la conversión de zona horaria y a un formato único de errores. C-01 dejó el esqueleto sin tablas de negocio ni piezas compartidas; sin C-02 cada change inventaría su propia versión de estas bases, con riesgo de filtrar datos personales en errores o de mezclar UTC con hora local (RN-AG-07, RN-GL-03, Ley 25.326).

Governance: **CRÍTICO** (modelos core que todo lo demás referencia). Este change solo produce artefactos de planificación; el líder aprueba el plan antes del apply.

## What Changes

- **Migración Flyway V2**: tablas `practice` (una sola fila garantizada por la base, sembrada en la propia migración) y `app_user` (email único sin distinguir mayúsculas, `password_hash`, `role` con `CHECK`, `failed_attempts`, `locked_until`, `must_change_password`, `full_name`, `license_number`, `created_at`, `last_login_at`), claves UUID (v7 generadas en la aplicación), `timestamptz` en UTC, FK indexada a `practice`, privilegios del rol `app` acotados (sin `INSERT`/`DELETE` sobre `practice`, sin `DELETE` sobre `app_user`).
- **Modelos**: `Practice` (módulo `shared`), `AppUser` y enum `Role { DENTIST }` (módulo `auth`), extensibles a `SECRETARY` en v2 con una migración nueva.
- **Módulo `shared`**: `Clock` inyectable (UTC), conversiones de zona horaria hacia/desde `America/Argentina/Buenos_Aires` (zona tomada del consultorio), contexto del `practice_id` de la instalación, y manejo global de errores con `ProblemDetail` y mensajes genéricos (sin datos personales, sin stack traces).
- **Hash de contraseñas**: Argon2id con parámetros OWASP vía `Argon2PasswordEncoder` de Spring Security, detrás de un puerto de dominio. **Nueva dependencia aprobada por el usuario**: Bouncy Castle (`org.bouncycastle:bcprov-jdk18on`).
- **Seed de bootstrap del DENTIST** al arrancar, desde `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` (no versionadas), con `must_change_password = true`, idempotente y sin loguear email ni contraseña. Si no existe ningún DENTIST y faltan las variables, el arranque falla nombrando la variable.
- **Arquitectura**: la capa `domain` pasa a tener prohibido depender de cualquier paquete de Spring (no solo Web/JDBC); se permiten explícitamente las anotaciones de mapeo `jakarta.persistence` y `org.hibernate.annotations`.
- **Tests**: repositorios y restricciones de base (PostgreSQL embebido zonky), conversiones de zona horaria, handler de errores, hash de contraseñas y seed idempotente. Sin Docker (DD-10).

Fuera de alcance: login, sesión, bloqueo por intentos, política final de contraseñas y respuestas 401/403 con `ProblemDetail` (C-04, PA-04); `audit_log` (C-03); parámetros de agenda en `practice` (`slot_granularity_min`, `min_notice_min`, `max_advance_days`; C-05, V5).

## Capabilities

### New Capabilities
- `practice-installation`: el consultorio único de la instalación (fila única, datos y zona horaria) y el contexto `practice_id` que usan los demás módulos.
- `user-accounts`: cuentas de usuario interno (modelo, rol, unicidad de email, almacenamiento de contraseñas con Argon2id).
- `dentist-bootstrap`: alta inicial idempotente del odontólogo desde variables de entorno.
- `time-and-zones`: reloj inyectable y conversiones entre UTC y la zona del consultorio.
- `error-responses`: formato único de errores de la API (`ProblemDetail`) sin datos sensibles.

### Modified Capabilities
- `module-boundaries`: el requisito "Dominio independiente del framework" se endurece (sin Spring en `domain`) y declara las anotaciones de persistencia permitidas.
- `runtime-configuration`: el requisito "Arranque falla ante configuración obligatoria faltante" suma las variables de bootstrap como obligatorias condicionales (solo si no existe un DENTIST).
- `database-migrations`: se agrega el requisito de privilegios restringidos del rol de aplicación sobre las tablas core.

## Impact

- **Código** (`backend/`): `shared.{api,application,domain,infrastructure}`, `auth.{application,domain,infrastructure}`, `db/migration/V2__core_models.sql`, `ModuleBoundaries` (ArchUnit), soporte de tests (`EmbeddedPostgresSupport` debe aportar variables de bootstrap a los IT).
- **Dependencias**: `org.bouncycastle:bcprov-jdk18on` (implementation) — aprobada por el usuario. `spring-boot-starter-validation` (previsto en C-01 D13 para C-02) para los tests del handler de validación.
- **Configuración**: `.env.example` documenta `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` con marcadores (sin valores reales); no se mapean en `application.yml`.
- **API**: sin endpoints nuevos; cambia el formato de los errores que generen los controllers futuros.
- **Changes que dependen de este**: C-03 (usa `practice_id`, `AppUser.id`, `Clock`), C-04 (usa `AppUser`, `PasswordHasher`, `must_change_password`, `failed_attempts`, `locked_until`), C-05 (agrega columnas a `practice`).
