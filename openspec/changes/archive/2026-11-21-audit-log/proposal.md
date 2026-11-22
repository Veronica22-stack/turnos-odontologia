# Proposal: C-03 `audit-log`

## Why

C-02 dejó el consultorio, las cuentas y el reloj listos, pero todavía no existe ningún mecanismo para registrar quién accede a datos clínicos ni de pacientes. Los changes que siguen en el camino crítico —C-04 (login), C-10/C-16 (historia clínica) y C-23 (gestión por token)— necesitan un puerto único de auditoría desde el primer día; si cada módulo escribe su propio log, se pierde la trazabilidad y aumenta el riesgo de filtrar datos sensibles (RN-AD-01..03, RN-HC-04, RN-HC-08, Leyes 25.326 y 26.529; ver KB 11).

Governance: **CRÍTICO** (audit trail). Este change solo produce artefactos de planificación; el líder aprueba el plan antes del apply.

## What Changes

- **Migración Flyway `V3__audit_log.sql`**: tabla `audit_log` append-only con `id bigint` PK, `practice_id` (FK a `practice`, NOT NULL, preparada para multi-tenant), `user_id` (FK nullable a `app_user`; NULL = anónimo/sistema), `action` (`text` + `CHECK`), `entity_type`, `entity_id`, `patient_id` (nullable), `ip` (`inet`), `user_agent` (`text`), `occurred_at` (`timestamptz`, UTC, servidor), `details` (`jsonb`). Índices para las consultas de trazabilidad. Conforme al patrón documentado en V1: `REVOKE UPDATE, DELETE` (y `TRUNCATE`) al rol `${app_role}`, conservando solo `INSERT`/`SELECT`. **No se edita V1 ni V2** (regla dura 3).
- **Módulo `auditoria`** con las cuatro capas `api`, `application`, `domain`, `infrastructure` (ya existe el esqueleto de `package-info.java`). Puerto público `auditoria.api.AuditLogger` con acciones `LOGIN_OK`, `LOGIN_FAIL`, `PATIENT_READ`, `PATIENT_UPDATE`, `HC_READ`, `HC_CREATE`, `HC_CORRECT`, `BOOKING_TOKEN_USE`, etc. Los módulos de negocio **solo reportan** eventos a través de ese puerto; no conocen la tabla ni el adaptador.
- **Aislamiento arquitectónico explícito**: `auditoria` MUST NOT depender de `agenda`, `reservas`, `pacientes`, `historiaclinica` ni `notificaciones` (directa ni transitivamente, en ninguna capa). Se expresa como restricción formal y se verifica con una regla ArchUnit dedicada con fixtures positivos y negativos.
- **Cero datos sensibles en claro**: ni contenido clínico, ni DNI, email, teléfono, nombre, contraseñas ni tokens de gestión en `details` ni en logs de aplicación. Estrategia de lista de permitidos (*allowlist*) de claves + sanitización/redacción antes de persistir, con valores escalares primitivos y solo identificadores/referencias. `details` queda `jsonb` para metadatos operacionales mínimos; la auditoría de HC registra `patient_id`/`entity_id`, nunca el contenido (RN-AD-03, RN-HC-08).
- **Sin endpoints, sin UI y sin dependencias nuevas**: los eventos se generan desde los casos de uso de otros módulos; no se expone API REST de consulta en este change (C-24 evalúa la interfaz de consulta).
- **Tests (Strict TDD)**: unitarios de dominio/aplicación (registro, sanitización, redacción, rechazo de claves sensibles), ArchUnit de fronteras, e integración `*IT` con PostgreSQL embebido (zonky) que demuestra que el rol `app` puede `INSERT`/`SELECT` pero **no** `UPDATE`/`DELETE`, y que un evento con datos sensibles no se persiste ni se loguea. Sin Docker (DD-10).

Fuera de alcance: login/sesión y el cableado de `LOGIN_OK/LOGIN_FAIL` (C-04), lectura/escritura de HC y su cableado (C-10/C-16), uso de tokens de reserva y su cableado (C-23), API de consulta/exportación de auditoría y política de retención (C-24 / PA-01, PA-03), cifrado a nivel columna.

## Capabilities

### New Capabilities
- `audit-log`: rastro de auditoría append-only — acciones registradas, metadatos de contexto (usuario, paciente, IP, `user_agent`, instante UTC), inmutabilidad, y prohibición de persistir o loguear datos clínicos o personales en claro.

### Modified Capabilities
- `database-migrations`: se agrega el requisito de tabla de auditoría inmutable creada en `V3`, con `REVOKE UPDATE, DELETE, TRUNCATE` al rol de aplicación y `INSERT`/`SELECT` permitidos.
- `module-boundaries`: se refuerza el requisito "Grafo de dependencias permitido" con una prohibición explícita y verificable de cualquier dependencia de `auditoria` hacia los cinco módulos de negocio, y con el puerto `auditoria.api` como único canal de entrada.

## Impact

- **Código** (`backend/`): `db/migration/V3__audit_log.sql`; paquetes `auditoria.{api,application,domain,infrastructure}` (tipos `AuditAction`, `AuditEvent`/comando, `AuditLogger`, sanitizador/redactor, servicio, adaptador JDBC o JPA con `Clock` de `shared` y `InstallationPractice`); regla nueva en `architecture/ModuleBoundaries` y sus tests/fixtures; soporte de IT existente (`EmbeddedPostgresSupport`) reutilizado.
- **Dependencias**: ninguna (se usa el stack ya aprobado: Spring Boot 4.0.10, Java 21, Gradle, PostgreSQL, ArchUnit, zonky). Sin cambios en `frontend/`.
- **Configuración**: ninguna variable nueva; `occurred_at` se toma del `Clock` inyectado de C-02 y `practice_id` de `InstallationPractice`.
- **API**: sin endpoints nuevos; cambia el contrato interno disponible para los módulos (nuevo puerto `AuditLogger`).
- **Changes que dependen de este**: C-04 (`auth-session`), C-10, C-16, C-23; C-24 consume el rastro para su interfaz de consulta.
