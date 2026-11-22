# Design: C-16 `clinical-record-core`

## Context

Ver motivación en `proposal.md` (Why) y contratos en `specs/`. Estado actual relevante (observado en el código, no supuesto):

- **Roles de BD**: Flyway corre con el rol de migración; la app con `${app_role}`. V1 da DML por defecto a `${app_role}` sobre toda tabla nueva; cada tabla inmutable debe **revocar** explícitamente (patrón ya usado en V3 `audit_log` y V7 `patient`).
- **Auditoría (C-03)**: puerto `auditoria.api.AuditLogger` + `AuditEvent.of(...)`. `HC_READ`, `HC_CREATE`, `HC_CORRECT` **ya están** en el enum `AuditAction` y en el CHECK de V3 → no se necesita migración de catálogo. `AuditLoggerService` es fail-closed (lanza `BusinessRuleException("AUDIT_WRITE_FAILED")`). El CHECK `audit_log_details_no_pii_check` prohíbe claves como `content`, `note`, `clinical`, `description` en `details`.
- **Auth (C-04)**: `SecurityConfig` ya aplica `/api/v1/** → DentistAccessManager` (sesión + `ROLE_DENTIST` + sin cambio de contraseña pendiente), CSRF por cabecera `X-XSRF-TOKEN`, y `SecurityHeadersFilter` ya envía `Cache-Control: no-store`. El principal expone el id de usuario como `authentication.getName()` (UUID), patrón usado por `PatientsController.currentUserId()`.
- **Pacientes (C-10, implementado sin archivar)**: entidad `Patient` (JPA en `pacientes.domain`), `PatientsService.findOrThrow` filtra por `InstallationPractice.id()`. `pacientes.api` hoy solo tiene DTOs: **no existe** un puerto para que otro módulo consulte un paciente.
- **Arquitectura (ArchUnit)**: `historiaclinica → {pacientes, auditoria}` ya permitido, **solo por la capa `api`**; `domain` no puede depender de Spring ni `java.sql` (sí de `jakarta.persistence` y `org.hibernate.annotations`); `application` no depende de `api`/`infrastructure` de su propio módulo. Patrón de puerto: interfaz en `X.api`, adaptador en `X.infrastructure` que delega en `X.application` (ej. `JdbcAuditLogger`).
- **Errores**: `ApiExceptionHandler` mapea `NotFoundException→404`, `ConflictException→409`, `BusinessRuleException→422`, validación → `400 VALIDATION_FAILED` con `errors[{field, code}]` **sin** eco del valor.
- **Reloj**: bean `java.time.Clock` UTC en `shared.infrastructure`; prohibido `Instant.now()` sin `Clock`.
- **Migraciones existentes**: V1, V2, V3, V5, V7 (V4/V6 reservadas a C-07/C-09, V8–V10 a C-11/C-12/C-14 en curso).

## Goals / Non-Goals

**Goals:**
- Esquema append-only para la HC con integridad de correcciones garantizada **en la base**, no solo en la app.
- Un mecanismo de corrección genérico que C-17/C-18 reutilicen agregando solo su tabla, su entidad y su controlador.
- Auditoría transaccional y fail-closed de toda lectura/escritura clínica.

**Non-Goals:**
- Odontograma (C-17), evoluciones (C-18), UI (C-22).
- Firma digital, sellado criptográfico, exportación/interoperabilidad (supuestos S-HC-01/02/05 del proposal; dependen de PA-01).
- Consulta del registro de accesos (US-064, otro change).
- Borrador local en el navegador (Flujo 6, error "sesión expirada") — es de la UI.

## Decisions

### D1: Tablas por tipo de entrada + convención común (no tabla única polimórfica)
- **Decisión**: `clinical_record` (contenedor) + una tabla por tipo (`anamnesis_entry` acá; `odontogram_entry`/`evolution_entry` en C-17/C-18). Cada tabla de entradas repite la **convención de columnas** de corrección:
  `id uuid PK`, `clinical_record_id uuid NOT NULL`, `corrects_entry_id uuid NULL`, `correction_reason text NULL`, `author_id uuid NOT NULL → app_user`, `created_at timestamptz NOT NULL`, y las mismas 4 constraints (D3).
- **Razón**: alinea con KB 04, permite constraints/índices específicos por tipo (FDI en odontograma, `appointment_id` en evoluciones) y FKs reales.
- **Alternativa descartada**: tabla única `clinical_entry(type, payload jsonb)`. Pierde validación de esquema por tipo y FKs específicas.

### D2: Esquema V11 (`V11__clinical_record.sql`)
```sql
CREATE TABLE clinical_record (
    id          uuid        PRIMARY KEY,
    practice_id uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    patient_id  uuid        NOT NULL REFERENCES patient (id)  ON DELETE RESTRICT,
    opened_at   timestamptz NOT NULL,
    opened_by   uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT clinical_record_patient_unique UNIQUE (patient_id)
);

CREATE TABLE anamnesis_entry (
    id                 uuid        PRIMARY KEY,
    clinical_record_id uuid        NOT NULL REFERENCES clinical_record (id) ON DELETE RESTRICT,
    version            integer     NOT NULL,
    content            jsonb       NOT NULL,
    corrects_entry_id  uuid        NULL,
    correction_reason  text        NULL,
    author_id          uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at         timestamptz NOT NULL,
    CONSTRAINT anamnesis_entry_version_check   CHECK (version >= 1),
    CONSTRAINT anamnesis_entry_content_check   CHECK (jsonb_typeof(content) = 'object'
                                                      AND octet_length(content::text) <= 16384),
    CONSTRAINT anamnesis_entry_record_version_unique UNIQUE (clinical_record_id, version),
    CONSTRAINT anamnesis_entry_record_id_unique      UNIQUE (clinical_record_id, id),
    -- convención de corrección (D3), idéntica en C-17/C-18:
    CONSTRAINT anamnesis_entry_correction_pair_check
        CHECK ((corrects_entry_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT anamnesis_entry_correction_reason_check
        CHECK (correction_reason IS NULL OR (btrim(correction_reason) <> '' AND char_length(correction_reason) <= 500)),
    CONSTRAINT anamnesis_entry_corrects_same_record_fk
        FOREIGN KEY (clinical_record_id, corrects_entry_id)
        REFERENCES anamnesis_entry (clinical_record_id, id) ON DELETE RESTRICT,
    CONSTRAINT anamnesis_entry_corrects_once_unique UNIQUE (corrects_entry_id)
);
CREATE INDEX clinical_record_practice_idx ON clinical_record (practice_id);
CREATE INDEX anamnesis_entry_author_idx   ON anamnesis_entry (author_id);

REVOKE UPDATE, DELETE, TRUNCATE ON clinical_record FROM ${app_role};
REVOKE UPDATE, DELETE, TRUNCATE ON anamnesis_entry FROM ${app_role};
```
- PK UUID v7 generados por la app (convención V2). `opened_at`/`created_at` **sin DEFAULT**: los fija la app con el `Clock` del servidor (D6).
- La FK compuesta con `MATCH SIMPLE` solo se evalúa cuando `corrects_entry_id` no es nulo; garantiza que la corrección quede en la **misma HC**.
- `UNIQUE (corrects_entry_id)` (NULLs distintos) → una entrada se corrige a lo sumo una vez: las correcciones forman una **cadena lineal**, y la "vigente" de una cadena es la que nadie corrige.

### D3: Inmutabilidad en tres capas
1. **BD**: `REVOKE UPDATE, DELETE, TRUNCATE` a `${app_role}` (incluye `clinical_record`: su contenido tampoco cambia).
2. **ORM**: entidades con `@org.hibernate.annotations.Immutable` y todas las columnas `updatable = false`; sin setters; los repositorios de dominio no exponen `delete*`.
3. **API**: solo `GET` y `POST`; no hay `PUT`/`PATCH`/`DELETE` (Spring responde `405` en formato único).
- **Alternativa evaluada y NO incluida por defecto**: trigger `BEFORE UPDATE OR DELETE ... RAISE EXCEPTION` que bloquee también al rol de migración. Más fuerte (defensa contra un operador/migración), pero impide futuras migraciones de datos legítimas. **Pendiente de aprobación** (ver Open Questions Q2).
- `SELECT ... FOR UPDATE` no está disponible para `${app_role}` (requiere privilegio UPDATE): por eso la concurrencia se resuelve con constraints (D5), no con locks de fila.

### D4: Mecanismo genérico de correcciones (reutilizable por C-17/C-18)
- **Dominio (`historiaclinica.domain`)**:
  - `ClinicalEntry` (`@MappedSuperclass`): `id`, `clinicalRecordId`, `correctsEntryId`, `correctionReason`, `authorId`, `createdAt`; constructor protegido que recibe un `EntryProvenance(authorId, createdAt)` y opcionalmente un `Correction(originalId, CorrectionReason)`.
  - `CorrectionReason`: value object (trim, no vacío, ≤ 500) — misma regla que el CHECK.
  - `ClinicalEntryType` (enum: `ANAMNESIS`; C-17/C-18 agregan constantes) con el `entity_type` de auditoría (`anamnesis_entry`) y el segmento de ruta (`anamnesis`).
  - `CorrectableEntryStore<E extends ClinicalEntry>`: `findInRecord(recordId, entryId)`, `isCorrected(entryId)`, `save(E)`, y hook `assertCorrectable(E original)` (default no-op).
- **Aplicación**: `ClinicalCorrections.correct(type, store, patientId, entryId, reason, actor, factory)` en una transacción:
  1. `PatientLookup` → 404 `PATIENT_NOT_FOUND` / 409 `PATIENT_INACTIVE`.
  2. HC del paciente; si no existe → 404 `CLINICAL_ENTRY_NOT_FOUND` (no se filtra la existencia de entradas de otro paciente).
  3. `findInRecord` → 404 `CLINICAL_ENTRY_NOT_FOUND`.
  4. `isCorrected` → 409 `CLINICAL_ENTRY_ALREADY_CORRECTED`; `store.assertCorrectable` (anamnesis: solo la vigente → 409 `CLINICAL_ENTRY_NOT_CURRENT`).
  5. `factory` construye la nueva entrada con `Correction(original.id, reason)` + autor/instante del servidor; `saveAndFlush`.
  6. `AuditLogger.log(HC_CORRECT, entity_id = nueva, details = {entry_type, corrects_entry_id})`.
  - Una violación de `*_corrects_once_unique` en el flush (carrera) se traduce a 409 `CLINICAL_ENTRY_ALREADY_CORRECTED`.
- **API**: cada tipo expone `POST /api/v1/patients/{patientId}/clinical-record/{segmento}/{entryId}/corrections` con body `{ reason, content }` tipado por su propio record. C-17/C-18 solo agregan: tabla (con la convención D2), entidad que extiende `ClinicalEntry`, store, DTOs y método de controlador.
- **Alternativa descartada**: un único endpoint `POST .../corrections` con `{entryType, entryId, payload}` y despacho dinámico. Pierde validación tipada (Bean Validation por record) y obliga a deserializar a mano.

### D5: Apertura de la HC y versionado sin locks
- **Apertura** (`ClinicalRecordService.openIfAbsent`): `INSERT ... ON CONFLICT (patient_id) DO NOTHING` + `SELECT` (query nativa en `infrastructure`). Idempotente y segura en paralelo sin UPDATE.
- **Versión de anamnesis**: `max(version)+1` dentro de la transacción; la carrera la detecta `anamnesis_entry_record_version_unique` → 409 `CLINICAL_RECORD_CONCURRENT_WRITE` (reintentable por el cliente). La transacción completa (HC abierta + entrada + auditoría) se revierte.
- **Alternativa**: `pg_advisory_xact_lock(hash(record_id))`. Más complejo y acopla a PostgreSQL desde la app; queda como mejora si los 409 molestan (improbable con un único usuario).

### D6: Autor, instante y contexto del servidor
- `authorId` = `UUID.fromString(authentication.getName())` (mismo patrón que `PatientsController`); `createdAt`/`openedAt` = `Instant.now(clock)`.
- Los request records **no tienen** campos `authorId`, `createdAt`, `id`, `version`, `clinicalRecordId`; Jackson (config por defecto de Spring Boot) ignora propiedades desconocidas → el cliente no puede fijarlos (test explícito).
- El controlador arma un `RequestContext(ip, userAgent)` desde `HttpServletRequest` y lo pasa al servicio para la auditoría (C-10 pasaba `null`; RN-AD-01 exige IP).
- **Alternativa**: `DEFAULT now()` en la BD con columna `insertable=false`. Igual de segura, pero rompe la convención de C-03 (reloj inyectable) y complica tests deterministas.

### D7: Contenido de anamnesis (PA-18, supuesto S-HC-08)
- `content` jsonb = `{ "schemaVersion": 1, "allergies", "medications", "medicalConditions", "habits", "notes" }`, todos texto opcional ≤ 2000, al menos uno no vacío. Mapeo con `@JdbcTypeCode(SqlTypes.JSON)` sobre un record de dominio `AnamnesisContent` (campos desconocidos no se persisten porque el record no los tiene).
- Si el profesional define otra estructura (PA-18), se sube `schemaVersion` sin migración; las versiones viejas se siguen leyendo.

### D8: Lectura, auditoría y puerto de pacientes
- `GET .../clinical-record` → `{opened, clinicalRecordId, openedAt, currentAnamnesis}`; `GET .../anamnesis` → `{current, history[]}` con `correctedByEntryId` calculado (una consulta por HC: entradas + mapa `corrects_entry_id → id`).
- Toda lectura con paciente existente registra **un** `HC_READ` (también si la HC no está abierta) con `entity_type` = `clinical_record` o `anamnesis_entry`. Las transacciones de lectura **no** son `readOnly` (la auditoría escribe en la misma transacción, igual que `PATIENT_READ` en C-10).
- `details` de auditoría solo con claves permitidas (snake_case): `entry_type`, `corrects_entry_id`, `version`. Nunca `content`/`reason` (además el CHECK de V3 rechaza `content`/`note`).
- **Puerto `pacientes.api.PatientLookup`**: `Optional<PatientStatus> findStatus(UUID patientId)` donde `PatientStatus(UUID id, boolean active)`; adaptador `PatientLookupAdapter` en `pacientes.infrastructure` que filtra por `InstallationPractice.id()`. No registra `PATIENT_READ` (no expone datos personales). Modifica código de C-10 (no archivado) → coordinar con su autor.

### D9: Versión de migración: **V11** (reservada) y riesgo de colisión
- Se usa **V11** tal como reserva `CHANGES.md`. Verificado: en `master` existen V1, V2, V3, V5, V7; las ramas remotas (`dev-elian`, `dev-elian-c04`, `dev-gabriel`, `dev-lucas`, `dev-mayra`) no tienen V8+ ni V11 al momento del propose.
- **Riesgo**: C-07 (V4), C-09 (V6), C-11 (V8), C-12 (V9), C-14 (V10) están en curso. Si V11 se aplica en una BD local/compartida **antes** que V8–V10, Flyway (sin `outOfOrder`) fallará al validar cuando lleguen esas versiones menores ("resolved migration not applied"). Ya ocurre el mismo patrón con V4/V6 frente a V5/V7.
  - Mitigación: los tests usan BD embebida nueva (sin impacto); en BD locales, recrear la base (`scripts` de C-01) tras merges fuera de orden; **no** habilitar `outOfOrder` en producción. Antes del merge, verificar con `git ls-tree` que nadie haya tomado V11.
  - Colisión directa (dos `V11__*.sql`) → Flyway falla al arrancar; el test `MigrationsIT` lo detecta en CI.

### D10: Logging y errores
- Records de request/response y `AnamnesisContent` **sobrescriben `toString()`** para no imprimir contenido ni motivo (patrón de `AuditEvent`).
- Los servicios solo loguean IDs. Ninguna excepción de dominio lleva contenido clínico en su mensaje.
- Códigos nuevos: `CLINICAL_ENTRY_NOT_FOUND` (404), `CLINICAL_ENTRY_ALREADY_CORRECTED` (409), `CLINICAL_ENTRY_NOT_CURRENT` (409), `CLINICAL_RECORD_CONCURRENT_WRITE` (409), `PATIENT_INACTIVE` (409); se reutiliza `PATIENT_NOT_FOUND` (404).

## Risks / Trade-offs

- **[Riesgo legal] PA-01 (Ley 27.706) abierta** → Supuestos S-HC-01..08 explícitos en el proposal; el modelo append-only permite agregar firma/sellado sin reescribir entradas. Bloquea despliegue (C-28), no desarrollo.
- **[Riesgo] Supresión (Ley 25.326) vs. inmutabilidad (PA-11)** → sin implementar; no hay forma de borrar desde la app. Resolución legal pendiente.
- **[Riesgo] Contenido clínico filtrado en logs** → `toString` sobrescritos, validación sin eco (ya garantizado por `ApiExceptionHandler`), test que captura logs y verifica ausencia del contenido.
- **[Trade-off] Rol de migración puede mutar la HC** → aceptado por defecto (Q2 propone trigger).
- **[Riesgo] Modificar `pacientes` (C-10 sin archivar)** → cambio aditivo (nuevo puerto + adaptador); delta spec `patients-management` con ADDED. Archivar C-10 antes que C-16.
- **[Trade-off] 409 ante escrituras concurrentes** → aceptable con un único profesional; el cliente reintenta.
- **[Riesgo] Colisión de versión de migración** → ver D9.

## Migration Plan

1. Merge de C-16 en orden respecto de V8–V10 cuando sea posible; si no, recrear BD locales.
2. Flyway aplica `V11__clinical_record.sql` con el rol de migración.
3. `ClinicalRecordPrivilegesIT` verifica que `${app_role}` tenga solo `SELECT`/`INSERT`.
4. Rollback: no hay rollback en caliente de una HC (datos legales). En entornos sin datos reales: restaurar backup previo o recrear la BD. Nunca un `V11` editado.

## Open Questions (requieren aprobación — governance CRÍTICO)

- **Q1 (PA-01/PA-11)**: aprobar los supuestos S-HC-01..08 del proposal como base del desarrollo, con validación legal antes de producción.
- **Q2**: ¿agregar trigger que bloquee UPDATE/DELETE también para el rol de migración? (defecto: no).
- **Q3 (PA-18)**: confirmar campos de anamnesis (`allergies`, `medications`, `medicalConditions`, `habits`, `notes`, ≤ 2000 c/u).
- **Q4**: confirmar que escribir en la HC de un paciente dado de baja se rechaza (`409 PATIENT_INACTIVE`) y que la lectura se permite.
- **Q5**: confirmar que la anamnesis solo admite corregir la versión vigente (las evoluciones de C-18 permitirán corregir cualquiera no corregida).

## Desviaciones durante el apply

1. **Allowlist del sanitizador de auditoría**: `SensitiveDataSanitizer` permite además las claves `entry_type`, `version` y `corrects_entry_id`. Un UUID exacto no se redacta únicamente bajo la clave `corrects_entry_id`; la lista negra no cambia. Los detalles usan snake_case (no camelCase como figuraba en D4/D8).
2. **POST sin sesión devuelve 403**: el filtro CSRF rechaza el POST antes de la autenticación, por lo que una escritura sin sesión responde 403 y no 401.
3. **Doble corrección concurrente**: dos correcciones simultáneas de la misma entrada terminan con una exitosa y la otra en `409 CLINICAL_RECORD_CONCURRENT_WRITE` (no `CLINICAL_ENTRY_ALREADY_CORRECTED`).
4. **IP del contexto de auditoría**: `RequestContext` usa `getRemoteAddr()`; no se interpreta `X-Forwarded-For`.
