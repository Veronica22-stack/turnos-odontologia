# Design: C-17 `odontogram`

## Context

Motivación en `proposal.md` (Why); contratos en `specs/clinical-odontogram` y `specs/database-migrations`. Estado actual observado en el código (no supuesto):

- **Núcleo C-16** (`historiaclinica`, archivado):
  - `domain.ClinicalEntry` (`@MappedSuperclass @Immutable`, columnas `updatable=false`; constructor protegido `(UUID clinicalRecordId, EntryProvenance, Correction)`; `toString` solo con IDs).
  - `domain.ClinicalEntryType` (enum con `pathSegment` y `auditEntityType`; hoy solo `ANAMNESIS`).
  - `domain.CorrectableEntryStore<E>`: `findInRecord`, `isCorrected`, `save` (con flush), `assertCorrectable` (default no-op).
  - `application.ClinicalCorrections.correct(type, store, patientId, entryId, rawReason, actorId, RequestContext, CorrectionFactory<E>)`: valida motivo (`CorrectionReason`), paciente escribible (`ClinicalPatientGuard.requireWritable` → 404/409), HC existente y entrada en la HC (404 `CLINICAL_ENTRY_NOT_FOUND`), `isCorrected` (409 `CLINICAL_ENTRY_ALREADY_CORRECTED`), `assertCorrectable`, construye con `factory.create(recordId, correction, provenance, original)`, guarda traduciendo `DataIntegrityViolationException` con `ClinicalWriteConflicts` (por nombre de constraint `*corrects_once_unique` / `*record_version_unique`) y audita `HC_CORRECT` con `{entry_type, corrects_entry_id}`.
  - `application.ClinicalRecordService.openForWrite(patientId, userId)` (abre la HC con `INSERT ... ON CONFLICT DO NOTHING`), `ClinicalPatientGuard.requireReadable`, `RequestContext(ip, userAgent)`.
  - `infrastructure.ClinicalRequestSupport.currentUserId()` / `contextOf(request)`; controladores con `@PreAuthorize("hasRole('DENTIST')")` y `Cache-Control: no-store`.
- **Auditoría**: `SensitiveDataSanitizer` ya permite `entry_type`, `version`, `corrects_entry_id`; el CHECK `audit_log_details_no_pii_check` de V3 bloquea `content`, `note`, etc. `audit_log.entity_type` es texto libre ≤ 100 → `odontogram_entry` no requiere migración.
- **Errores**: `ApiExceptionHandler` mapea validación a `400 VALIDATION_FAILED` con `errors[{field, code}]` sin eco, `NotFoundException`→404, `ConflictException`→409, `BusinessRuleException`→422.
- **Migraciones** en `dev-veronica`: V1, V2, V3, V5, V7, V11. Ninguna rama remota (`dev-elian`, `dev-elian-c04`, `dev-elian-c09`, `dev-gabriel`, `dev-lucas`, `dev-mayra`, `master`) tiene V12 (verificado con `git ls-tree` el 2026-11-22).

## Goals / Non-Goals

**Goals:**
- Agregar el odontograma como **segundo tipo** de entrada clínica tocando solo lo que C-16 previó como punto de extensión (constante del enum, tabla, entidad, store, fábrica, DTOs, controlador).
- Reglas de dominio (FDI, catálogo, cara) garantizadas en la base además de la API.
- Estado actual derivado de forma determinista y testeable sin SQL específico.

**Non-Goals:**
- UI del odontograma (C-22).
- Periodontograma, plan de tratamiento, presupuestos (fuera del MVP, KB 01).
- Estados "planificado vs. realizado", colores, prótesis/puentes multi-pieza, caras adicionales (cervical, etc.).
- Incluir el odontograma en `GET .../clinical-record` (resumen): queda como está; C-22 decide si lo necesita.
- Cambios en `SensitiveDataSanitizer`, `SecurityConfig`, `pacientes` o `auditoria`.

## Decisions

### D1: Tabla `odontogram_entry` en `V12__odontogram_entry.sql`
```sql
CREATE TABLE odontogram_entry (
    id                 uuid        PRIMARY KEY,
    clinical_record_id uuid        NOT NULL REFERENCES clinical_record (id) ON DELETE RESTRICT,
    tooth_code         text        NOT NULL,
    surface            text        NULL,
    condition          text        NOT NULL,
    notes              text        NULL,
    corrects_entry_id  uuid        NULL,
    correction_reason  text        NULL,
    author_id          uuid        NOT NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    created_at         timestamptz NOT NULL,
    -- FDI: permanente 11-18..41-48, temporal 51-55..81-85 (S-OD-01, Q1)
    CONSTRAINT odontogram_entry_tooth_code_check CHECK (tooth_code ~ '^([1-4][1-8]|[5-8][1-5])$'),
    CONSTRAINT odontogram_entry_condition_check
        CHECK (condition IN ('HEALTHY','CARIES','FILLED','MISSING','CROWN','ROOT_CANAL')),
    CONSTRAINT odontogram_entry_surface_check
        CHECK (surface IS NULL OR surface IN ('OCCLUSAL','MESIAL','DISTAL','VESTIBULAR','LINGUAL')),
    CONSTRAINT odontogram_entry_surface_condition_check
        CHECK (surface IS NULL OR condition IN ('CARIES','FILLED')),
    CONSTRAINT odontogram_entry_notes_check CHECK (notes IS NULL OR char_length(notes) <= 500),
    CONSTRAINT odontogram_entry_record_id_unique UNIQUE (clinical_record_id, id),
    -- Convención de corrección de C-16 (idéntica a anamnesis_entry):
    CONSTRAINT odontogram_entry_correction_pair_check
        CHECK ((corrects_entry_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT odontogram_entry_correction_reason_check
        CHECK (correction_reason IS NULL OR (btrim(correction_reason) <> '' AND char_length(correction_reason) <= 500)),
    CONSTRAINT odontogram_entry_corrects_same_record_fk
        FOREIGN KEY (clinical_record_id, corrects_entry_id)
        REFERENCES odontogram_entry (clinical_record_id, id) ON DELETE RESTRICT,
    CONSTRAINT odontogram_entry_corrects_once_unique UNIQUE (corrects_entry_id)
);
CREATE INDEX odontogram_entry_record_position_idx
    ON odontogram_entry (clinical_record_id, tooth_code, surface, created_at DESC);
CREATE INDEX odontogram_entry_author_idx ON odontogram_entry (author_id);

REVOKE UPDATE, DELETE, TRUNCATE ON odontogram_entry FROM ${app_role};
```
- `tooth_code` como `text` de dos dígitos, tal como KB 04 (evita perder el cero semántico y coincide con lo que la API expone). Alternativa `smallint` + CHECK aritmético: descartada para no divergir de la KB y de la API.
- Catálogos como `CHECK` de texto (patrón del CHECK de acciones de V3). Alternativa tabla catálogo: descartada; el catálogo es fijo en el MVP y ampliarlo vía migración nueva es aceptable (S-OD-02).
- El constraint de corrección conserva el sufijo `corrects_once_unique` para que `ClinicalWriteConflicts` traduzca la carrera a `409 CLINICAL_ENTRY_ALREADY_CORRECTED` **sin modificar** esa clase. No hay columna `version` (cada posición evoluciona independiente), por lo que la desviación 3 de C-16 (`CLINICAL_RECORD_CONCURRENT_WRITE` en doble corrección) no aplica aquí.
- Sin `DEFAULT` en `created_at`: lo fija la app con el `Clock` (convención C-16 D6).

### D2: Dominio (`historiaclinica.domain`)
- `ClinicalEntryType.ODONTOGRAM("odontogram", "odontogram_entry")`.
- Value objects (records, sin Spring, `toString` sin contenido donde aplique):
  - `ToothCode.of(String)`: valida con la misma regla que el CHECK; expone `dentition()` (`PERMANENT`/`TEMPORARY`) por si C-22 lo usa.
  - `ToothSurface` (enum de 5) y `ToothCondition` (enum de 6) con `allowsSurface()` (`true` solo `CARIES`, `FILLED`).
  - `ToothPosition(ToothCode, ToothSurface|null)`: clave de derivación.
- `OdontogramEntry extends ClinicalEntry` (`@Entity @Immutable @Table("odontogram_entry")`), columnas `updatable=false`, sin setters, `@Enumerated(STRING)` para cara/estado, `notes` ≤ 500. Fábricas:
  - `register(recordId, ToothPosition, ToothCondition, notes, EntryProvenance)`.
  - `correct(recordId, OdontogramEntry original, ToothCondition, notes, Correction, EntryProvenance)` → copia `tooth_code` y `surface` de la original (la posición no se corrige, Q4).
  - Ambas rechazan cara con estado de pieza completa (`IllegalArgumentException` de dominio; la API lo valida antes, ver D5).
- `OdontogramEntryRepository extends CorrectableEntryStore<OdontogramEntry>` + `findNewestFirst(recordId)` (orden `created_at DESC, id DESC`) + `existsNewerAtPosition(recordId, toothCode, surface, createdAt, id)`.
- **`OdontogramState.derive(List<OdontogramEntry> newestFirst)`** (función pura): construye el índice `corrects_entry_id → id`, descarta las entradas corregidas y toma la primera por `ToothPosition`. Devuelve `current` (ordenado por pieza FDI y cara) e `history` con `correctedByEntryId`.
  - **Alternativa** `SELECT DISTINCT ON (tooth_code, surface) ... WHERE NOT EXISTS (corrección) ORDER BY tooth_code, surface, created_at DESC, id DESC`: equivalente, pero el historial completo ya se lee para la respuesta (una sola consulta) y la función pura se prueba sin base. Se descarta la consulta extra.
  - Desempate: mismo `created_at` (lote) nunca comparte posición (validado); entre requests distintos con igual instante decide `id` (UUID v7, monótono).

### D3: Correcciones reutilizando `ClinicalCorrections` (sin tocarlo)
- `OdontogramCorrectionStore implements CorrectableEntryStore<OdontogramEntry>` (`application`, `@Component`), delega en el repositorio y sobrescribe `assertCorrectable(original)`: si `existsNewerAtPosition(...)` → `ConflictException("CLINICAL_ENTRY_NOT_CURRENT")` (Q4). Motivo: una corrección se crea "ahora"; si se permitiera corregir una entrada ya superada, la corrección pasaría a ser la más reciente de su posición y pisaría un estado posterior válido.
- `OdontogramService.correct(...)` llama a `corrections.correct(ClinicalEntryType.ODONTOGRAM, store, patientId, entryId, reason, actor, ctx, (recordId, correction, provenance, original) -> OdontogramEntry.correct(recordId, original, condition, notes, correction, provenance))`.
- Si la nueva condición no admite la cara de la original (p. ej. `36/OCCLUSAL` corregida a `MISSING`), la lambda de fábrica del servicio comprueba `condition.allowsSurface()` contra la cara de la original y lanza `BusinessRuleException("ODONTOGRAM_SURFACE_NOT_ALLOWED")` → `422`. La fábrica corre antes de `save` y de la auditoría, así que no se escribe nada y la transacción se revierte.
- Ruta uniforme: `POST .../clinical-record/odontogram/{entryId}/corrections`. Una entrada de anamnesis enviada por esta ruta no está en `odontogram_entry` → 404 `CLINICAL_ENTRY_NOT_FOUND` (aislamiento por tipo).

### D4: Casos de uso (`OdontogramService`, `application`)
- `register(patientId, userId, List<OdontogramEntryCommand>, ctx)` `@Transactional`: `recordService.openForWrite` (valida paciente activo, abre HC) → crea y guarda cada entrada con el mismo `Instant.now(clock)` → un `HC_CREATE` por entrada (`entity_type=odontogram_entry`, `entity_id` = id, `details={entry_type: ODONTOGRAM}`). Cualquier falla (validación de dominio, constraint, auditoría) revierte el lote completo.
- `read(patientId, userId, ctx)` `@Transactional` (no `readOnly`, audita): `patientGuard.requireReadable` → si hay HC, `findNewestFirst` + `OdontogramState.derive`; si no, vacío sin abrir HC → un `HC_READ` (`entity_id` nulo, `details` vacío, igual que anamnesis).
- `correct(...)`: D3.
- Los comandos y datos de salida (`OdontogramEntryCommand`, `OdontogramEntryData`, `OdontogramData`) son records de `application` con `toString` sin notas/motivo.

### D5: API (`historiaclinica.api` + `infrastructure.OdontogramController`)
- `GET  /api/v1/patients/{patientId}/clinical-record/odontogram` → `200 OdontogramResponse{current[], history[]}`.
- `POST /api/v1/patients/{patientId}/clinical-record/odontogram` body `OdontogramRequest{entries: [ {toothCode, surface?, condition, notes?} ]}` → `201 OdontogramEntriesResponse{entries[]}`.
- `POST /api/v1/patients/{patientId}/clinical-record/odontogram/{entryId}/corrections` body `OdontogramCorrectionRequest{reason, condition, notes?}` → `201 OdontogramEntryResponse`.
- `OdontogramEntryResponse{id, toothCode, surface, condition, notes, authorId, createdAt, correctsEntryId, correctionReason, correctedByEntryId}`.
- Bean Validation: `@NotNull @Size(min=1,max=64) List<@Valid @NotNull OdontogramEntryRequest>`; `toothCode` `@NotBlank @Pattern("^([1-4][1-8]|[5-8][1-5])$")`; `condition`/`surface` como `String` con `@Pattern` del catálogo (no enums Jackson, para obtener `400 VALIDATION_FAILED` con campo y sin eco en lugar de un error de deserialización); `notes` `@Size(max=500)`; `reason` `@NotBlank @Size(max=500)`. Restricciones de clase propias: `@SurfaceAllowedForCondition` (por ítem) y `@DistinctToothPositions` (en el request).
- Records con `toString` sobrescrito (sin notas ni motivo). Request records **sin** `id`, `authorId`, `createdAt`, `clinicalRecordId`; el de corrección **sin** `toothCode`/`surface` (Jackson ignora desconocidos).
- Controlador con `@PreAuthorize("hasRole('DENTIST')")`, `Cache-Control: no-store`, `ClinicalRequestSupport.currentUserId()/contextOf()`; el mapeo a DTO se agrega a `ClinicalRequestSupport` (o un `OdontogramMapping` package-private si crece). Sin `PUT/PATCH/DELETE` → `405` en formato único (comportamiento existente).
- Seguridad: `/api/v1/**` ya exige `DENTIST` + sin cambio de contraseña pendiente; POST requiere CSRF (sin sesión → 403 por CSRF, desviación 2 de C-16).

### D6: Auditoría y logging
- Reutiliza `AuditLogger` (fail-closed, misma transacción). `details` solo `entry_type` y, en correcciones, `corrects_entry_id` (ya en la allowlist). **No** se agrega `tooth_code` ni `condition` a `details`: el `entity_id` alcanza para trazar y evita ampliar la allowlist con datos clínicos.
- Logs solo con IDs; ninguna excepción lleva notas/motivo/pieza en el mensaje.

### D7: Versión de migración V12 y riesgo de colisión
- Se usa **V12** (reservada en `CHANGES.md`; C-18 reserva V13). Verificado libre en todas las ramas remotas.
- Riesgo heredado de C-16 D9: V4, V6, V8–V10 siguen sin aplicarse en otras ramas; en BD locales ya migradas a V11/V12, Flyway (sin `outOfOrder`) fallará cuando lleguen esas versiones → recrear la BD local. Tests usan BD embebida nueva (sin impacto). Re-verificar con `git ls-tree` antes del merge.

## Risks / Trade-offs

- **[Riesgo legal] PA-01 abierta** → se heredan S-HC-01..08 de C-16; bloquea despliegue, no desarrollo.
- **[Riesgo de dominio] PA-17 abierta (catálogo, caras, dentición)** → supuestos S-OD-01..03 explícitos; cambiar el catálogo es una migración nueva que relaja/reemplaza el CHECK (nunca editar V12).
- **[Trade-off] La posición no se corrige** → un error de pieza se resuelve corrigiendo la entrada equivocada a su estado real (p. ej. `HEALTHY`, motivo "pieza equivocada") y registrando la pieza correcta. Más simple y sin ambigüedad temporal; ver Q4.
- **[Trade-off] Pieza completa y caras son posiciones independientes** → un `MISSING` posterior no "oculta" caries previas por cara en la API; la UI (C-22) decide la presentación. Ver Q6.
- **[Riesgo] Carrera corrección vs. alta en la misma posición** → ambas confirman; gana la de mayor `(created_at, id)`. Aceptable con un único profesional.
- **[Riesgo] Datos clínicos en logs** → `toString` sobrescritos, validación sin eco, test de marcador (`OdontogramDataLeakageIT`).
- **[Riesgo] Historial grande en un solo GET** → volumen bajo por paciente (≤ cientos de filas); paginación queda para C-22 si hiciera falta.

## Migration Plan

1. Merge de C-17 después de C-16 (ya en `dev-veronica`); re-verificar que V12 siga libre.
2. Flyway aplica `V12__odontogram_entry.sql` con el rol de migración.
3. `OdontogramPrivilegesIT` verifica que `${app_role}` solo tenga `SELECT`/`INSERT`.
4. Rollback: no hay rollback en caliente de datos clínicos; en entornos sin datos reales, recrear la BD. Nunca editar V12.

## Open Questions (requieren aprobación — governance CRÍTICO)

Las tareas asumen el valor por defecto de cada una; si alguna cambia, se actualizan specs/tasks antes del apply.

- **Q1 (PA-17, dentición)**: ¿aceptar permanente (11–48) **y** temporal (51–85) en el mismo odontograma? *Defecto: sí (dentición mixta).*
- **Q2 (PA-17, catálogo)**: confirmar el catálogo base `HEALTHY`, `CARIES`, `FILLED`, `MISSING`, `CROWN`, `ROOT_CANAL` (códigos en inglés en API/BD; etiquetas en español en la UI). *Defecto: sí.*
- **Q3 (caras, KB 04 `surface` nullable)**: ¿caras en alcance? *Defecto: sí, opcionales, 5 caras (`OCCLUSAL`, `MESIAL`, `DISTAL`, `VESTIBULAR`, `LINGUAL`), solo para `CARIES`/`FILLED`.* Alternativa mínima: solo pieza completa (sin columna `surface`); agregarla luego exigiría una migración y redefinir la derivación.
- **Q4 (corrección)**: solo es corregible la entrada **vigente** de su posición y la corrección **no cambia pieza ni cara**. *Defecto: sí* (consistente con Q5 de C-16).
- **Q5 (alta por lote)**: `POST` con 1..64 estados en una transacción y **un `HC_CREATE` por entrada**. *Defecto: sí.* Alternativa: un estado por request.
- **Q6 (derivación)**: pieza sin registros = sin estado (no se asume `HEALTHY`); pieza completa y cada cara son posiciones independientes. *Defecto: sí.*
- **Q7 (herencia C-16)**: aplicar a C-17 las decisiones aprobadas en C-16 (S-HC-01..08, sin trigger anti-UPDATE para el rol de migración, lectura de inactivos permitida y escritura `409 PATIENT_INACTIVE`). *Defecto: sí.*

## Resolución de las preguntas y desviaciones durante el apply

**Q1–Q7 aprobadas por el usuario (2026-11-22)** con los valores por defecto propuestos.

Desviaciones menores de implementación (ninguna altera esquema, seguridad, endpoints ni inmutabilidad):

1. `OdontogramEntryRepository.existsNewerAtPosition(recordId, OdontogramEntry reference)` recibe la entrada de referencia en lugar de `(recordId, toothCode, surface, createdAt, id)`; internamente usa dos consultas JPQL (pieza completa / cara) para evitar parámetros enum nulos.
2. `OdontogramState.derive` ordena por `(createdAt, id)` por sí misma (no depende del orden de entrada) y devuelve `OdontogramState(current, history)` con `Item(entry, correctedByEntryId)`.
3. Los validadores de clase de la API (`SurfaceAllowedForCondition`, `DistinctToothPositions`) reportan sus violaciones sobre `surface` y `entries`.
4. Desempate por id: Java compara `UUID` con signo y PostgreSQL sin signo; es consistente porque los UUID v7 tienen el bit alto en 0.
5. Núcleo de C-16 sin cambios: solo se agregó `ClinicalEntryType.ODONTOGRAM` y un `toResponse` en `ClinicalRequestSupport` (permitido por D5).
