# Design: C-18 `evolutions`

> **Plan approval: APROBADO por Veronica el 2026-11-22.** Decisiones: V15; `historiaclinica` → `agenda.api` reutilizando `AppointmentNotificationLookup`; descripción ≤ 5000 y motivo ≤ 500; sin `performed_at` (solo `created_at` del servidor, desvío documentado de KB 04); turno vinculable del mismo paciente, no `CANCELLED`/`NO_SHOW` y ya comenzado; correcciones encadenadas por el mecanismo de C-16 conservando el turno; endpoints sin `PUT`/`PATCH`/`DELETE` ni paginación; historial **más reciente primero** (`created_at DESC, id DESC`, **cambio respecto de lo propuesto**); auditoría `HC_READ`/`HC_CREATE`/`HC_CORRECT`; la evolución nunca cambia el estado del turno; PA-01 abierta con los supuestos de C-16 heredados. Detalle en "Open Questions".

## Context

Motivación en `proposal.md` (Why); contratos en `specs/clinical-evolutions`, `specs/database-migrations` y `specs/module-boundaries`. Estado actual observado en el código de `dev-veronica` (no supuesto):

- **Núcleo C-16 + extensión C-17** (`historiaclinica`, archivados):
  - `domain.ClinicalEntry` (`@MappedSuperclass @Immutable`, columnas `updatable=false`, id UUID v7, constructor protegido `(clinicalRecordId, EntryProvenance, Correction)`, `toString` solo con IDs).
  - `domain.ClinicalEntryType` con `ANAMNESIS` y `ODONTOGRAM`; su javadoc ya anuncia la constante de C-18.
  - `domain.CorrectableEntryStore<E>` (`findInRecord`, `isCorrected`, `save`, `assertCorrectable` default no-op) y `application.ClinicalCorrections.correct(...)` (motivo, paciente escribible, entrada en la HC → 404 `CLINICAL_ENTRY_NOT_FOUND`, `isCorrected` → 409 `CLINICAL_ENTRY_ALREADY_CORRECTED`, fábrica, `save` con traducción por sufijo `*corrects_once_unique`, auditoría `HC_CORRECT {entry_type, corrects_entry_id}`).
  - `ClinicalRecordService.openForWrite`, `ClinicalPatientGuard.requireReadable/requireWritable` (`PATIENT_NOT_FOUND` 404, `PATIENT_INACTIVE` 409), `RequestContext`, `ClinicalRequestSupport.currentUserId()/contextOf()`.
  - `OdontogramService` es la plantilla directa: alta con `Clock`, `HC_CREATE` por entrada, lectura `@Transactional` (no `readOnly`) con `HC_READ` (`entity_id` nulo), corrección vía `ClinicalCorrections` con lambda de fábrica.
- **Agenda (C-11)**: `agenda.api.AppointmentNotificationLookup.find(UUID) → Optional<AppointmentNotificationView(appointmentId, patientId, serviceName, startsAt, endsAt, AppointmentStatusView)>`; vacío si el turno no existe en el consultorio; solo lectura. Lo consume hoy `notificaciones`. `appointment` revoca `DELETE`/`TRUNCATE` al rol app (V13) y sus estados son `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.
- **Límites de módulos**: `ModuleBoundaries.ALLOWED_DEPENDENCIES` (test) fija `HISTORIA_CLINICA → {PACIENTES, AUDITORIA}`; el spec `module-boundaries` y KB 08 dicen lo mismo. `agenda` → `pacientes` solamente, por lo que `historiaclinica` → `agenda` no genera ciclo.
- **Auditoría**: `SensitiveDataSanitizer` permite `entry_type`, `version`, `corrects_entry_id`; `audit_log.entity_type` es texto libre ≤ 100 → `evolution_entry` no requiere migración.
- **Migraciones** (verificado con `git ls-tree` el 2026-11-22): `dev-veronica` llega a **V14** (`notification`); `master` y `dev-lucas` a V13; ninguna rama remota tiene V15. `CHANGES.md` reservaba V13 (desactualizado).
- **Helpers de test** que borran `clinical_record` o `appointment` (`ClinicalRecordSchemaIT`, `ClinicalRecordPrivilegesIT`, `Odontogram*IT`, `Clinical*IT`, `AppointmentFixtures.cleanAll`, `AppointmentPrivilegesIT`, `AppointmentConcurrencyIT`): con la FK nueva fallarían si quedan evoluciones.

## Goals / Non-Goals

**Goals:**
- Agregar las evoluciones como **tercer tipo** de entrada clínica tocando solo los puntos de extensión de C-16 (constante, tabla, entidad, store, fábrica, DTOs, controlador).
- Asociación a turno validada por la `api` de `agenda`, sin modificar `agenda`.
- Historial más reciente primero, con orden determinista y la marca de corrección (RN-HC-07).

**Non-Goals:**
- UI de evoluciones (C-22).
- `performed_at` clínico editable (S-EV-01, Q2), plantillas por prestación, vínculo con odontograma, adjuntos, firma digital.
- Cambiar el estado del turno al registrar una evolución (S-EV-03, Q6).
- Filtro por turno (`?appointmentId=`), paginación (Q7) y evoluciones en `GET .../clinical-record` (resumen).
- Cambios en `agenda`, `pacientes`, `auditoria`, `SecurityConfig`, `SensitiveDataSanitizer`, `ClinicalCorrections`, `ClinicalWriteConflicts`.

## Decisions

### D1: Tabla `evolution_entry` en `V15__evolution_entry.sql`

> CHECK de `description`: `description ~ '\S'` (al menos un caracter no blanco; `btrim` solo quitaba espacios y dejaba pasar saltos de linea/tabs). Aprobado por Veronica 2026-11-22. V15 no esta aplicada en ningun entorno compartido, por eso se edita in situ.

```sql
CREATE TABLE evolution_entry (
    id                 uuid        PRIMARY KEY,
    clinical_record_id uuid        NOT NULL REFERENCES clinical_record (id) ON DELETE RESTRICT,
    appointment_id     uuid        NULL     REFERENCES appointment (id)     ON DELETE RESTRICT,
    description        text        NOT NULL,
    corrects_entry_id  uuid        NULL,
    correction_reason  text        NULL,
    author_id          uuid        NOT NULL REFERENCES app_user (id)        ON DELETE RESTRICT,
    created_at         timestamptz NOT NULL,
    CONSTRAINT evolution_entry_description_check
        CHECK (description ~ '\S' AND char_length(description) <= 5000),
    CONSTRAINT evolution_entry_record_id_unique UNIQUE (clinical_record_id, id),
    -- Convención de corrección de C-16 (idéntica a anamnesis_entry y odontogram_entry):
    CONSTRAINT evolution_entry_correction_pair_check
        CHECK ((corrects_entry_id IS NULL) = (correction_reason IS NULL)),
    CONSTRAINT evolution_entry_correction_reason_check
        CHECK (correction_reason IS NULL OR (btrim(correction_reason) <> '' AND char_length(correction_reason) <= 500)),
    CONSTRAINT evolution_entry_corrects_same_record_fk
        FOREIGN KEY (clinical_record_id, corrects_entry_id)
        REFERENCES evolution_entry (clinical_record_id, id) ON DELETE RESTRICT,
    CONSTRAINT evolution_entry_corrects_once_unique UNIQUE (corrects_entry_id)
);
CREATE INDEX evolution_entry_record_created_idx ON evolution_entry (clinical_record_id, created_at DESC, id DESC);
CREATE INDEX evolution_entry_appointment_idx    ON evolution_entry (appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX evolution_entry_author_idx         ON evolution_entry (author_id);

REVOKE UPDATE, DELETE, TRUNCATE ON evolution_entry FROM ${app_role};
```
- Cabecera de la migración: por qué V15 (y no V13 como `CHANGES.md`), desvío respecto de KB 04 (sin `performed_at`), sin tocar V1–V14.
- `description` como `text` con CHECK (no `varchar(n)`), patrón del resto del esquema.
- FK a `appointment` entre módulos: mismo criterio que `notification` (V14). Garantiza existencia; la regla "mismo paciente" y los estados se validan en la aplicación (D4). **Alternativa** (trigger o FK compuesta vía `patient_id` desnormalizado): descartada; agrega acoplamiento de esquema entre módulos y la escritura es única (un profesional). Ver Q3.
- Sin `DEFAULT` en `created_at` (lo fija la app con el `Clock`, convención C-16 D6). Sin `practice_id` (como las demás entradas clínicas; se hereda vía `clinical_record`).
- El sufijo `corrects_once_unique` permite que `ClinicalWriteConflicts` traduzca la carrera de doble corrección a `409 CLINICAL_ENTRY_ALREADY_CORRECTED` sin modificarla. Sin columna `version`.

### D2: Dominio (`historiaclinica.domain`)
- `ClinicalEntryType.EVOLUTION("evolutions", "evolution_entry")` (segmento en plural, como pide la ruta de `CHANGES.md` y Flujo 6).
- `EvolutionDescription.of(String)` (record): no nula, no en blanco tras `strip`, ≤ 5000; `toString` sin contenido. Se guarda el texto tal cual (sin `strip`), la validación de vacío usa `isBlank` igual que el CHECK.
- `EvolutionEntry extends ClinicalEntry` (`@Entity @Immutable @Table("evolution_entry")`), columnas `appointment_id` y `description` `updatable=false`, sin setters. Fábricas:
  - `register(recordId, EvolutionDescription, UUID appointmentIdOrNull, EntryProvenance)`.
  - `correct(recordId, EvolutionEntry original, EvolutionDescription, Correction, EntryProvenance)` → copia `appointmentId` de la original (Q5).
- `EvolutionEntryRepository extends CorrectableEntryStore<EvolutionEntry>` + `findNewestFirst(recordId)` (orden `created_at DESC, id DESC`, igual que el odontograma de C-17).
- **`EvolutionHistory.of(List<EvolutionEntry>)`** (función pura): ordena por `(createdAt DESC, id DESC)` por sí misma (no depende del orden de entrada) y calcula `correctedByEntryId` con el índice `corrects_entry_id → id`. Se prueba sin base.
- **Desempate determinista**: ante igual `created_at` decide el `id` descendente (UUID v7, monótono). Java compara `UUID` con signo y PostgreSQL sin signo; es consistente porque los UUID v7 tienen el bit alto en 0 (misma nota que C-17). Así dos lecturas devuelven siempre el mismo orden.

### D3: Correcciones reutilizando `ClinicalCorrections` (sin tocarlo)
- `EvolutionCorrectionStore implements CorrectableEntryStore<EvolutionEntry>` (`application`, `@Component`), delega en el repositorio; **no** sobrescribe `assertCorrectable`: cualquier evolución no corregida es corregible (las evoluciones no tienen "posición" vigente como el odontograma). Las cadenas `A ← B ← C` quedan permitidas; corregir `A` dos veces → 409 por `isCorrected` / `UNIQUE`.
- `EvolutionService.correct(...)` → `corrections.correct(ClinicalEntryType.EVOLUTION, store, patientId, entryId, reason, actor, ctx, (recordId, correction, provenance, original) -> EvolutionEntry.correct(recordId, original, description, correction, provenance))`.
- El turno no se revalida en la corrección: se hereda tal cual de la original, que ya fue validada al alta (el turno no puede borrarse y sus estados terminales no cambian el hecho clínico).
- Ruta uniforme: `POST .../clinical-record/evolutions/{entryId}/corrections`. Una entrada de anamnesis u odontograma no está en `evolution_entry` → 404 `CLINICAL_ENTRY_NOT_FOUND` (aislamiento por tipo).

### D4: Asociación a turno (`EvolutionAppointmentGuard`, `application`)
- Consume `agenda.api.AppointmentNotificationLookup` **existente, sin cambios** (su spec ya la ofrece "a otros módulos"). Reglas, en este orden, con `Clock` del servidor:
  1. `find(appointmentId)` vacío **o** `view.patientId() ≠ patientId` → `BusinessRuleException("EVOLUTION_APPOINTMENT_NOT_FOUND")` → 422 (no se distingue "otro paciente" de "inexistente").
  2. `status ∈ {CANCELLED, NO_SHOW}` → 422 `EVOLUTION_APPOINTMENT_NOT_ATTENDED`.
  3. `startsAt > now` → 422 `EVOLUTION_APPOINTMENT_NOT_STARTED` (mismo criterio que `APPOINTMENT_NOT_STARTED` de C-11: inicio ≤ ahora es válido).
- Sin `appointmentId` no se consulta `agenda`.
- **Alternativa**: interfaz nueva `AppointmentClinicalLookup` en `agenda.api` (sin `serviceName`). Descartada para no tocar `agenda` en un change CRÍTICO de otro módulo; el nombre "Notification" queda como deuda menor (renombrar a `AppointmentLookup` en un refactor de `agenda`). Ver Q4.
- **Carrera** (turno cancelado entre la validación y el `INSERT`): aceptable con un único profesional; la evolución conserva el vínculo (el turno existió).

### D5: Casos de uso (`EvolutionService`, `application`)
- `register(patientId, userId, EvolutionCommand{description, appointmentId?}, ctx)` `@Transactional`: `patientGuard.requireWritable` → `appointmentGuard.check(...)` si hay turno → `recordService.openForWrite` → `save` (traduciendo `DataIntegrityViolationException`) → `HC_CREATE` (`entity_type=evolution_entry`, `entity_id`=id, `details={entry_type: EVOLUTION}`). La validación del turno va **antes** de abrir la HC para no abrirla en un rechazo (la transacción igual revertiría).
- `read(patientId, userId, ctx)` `@Transactional` (no `readOnly`, audita): `requireReadable` → si hay HC, `findNewestFirst` + `EvolutionHistory.of`; si no, lista vacía sin abrir HC → un `HC_READ` (`entity_id` nulo, `details` vacío).
- `correct(...)`: D3.
- Records `EvolutionCommand`, `EvolutionEntryData` con `toString` sin descripción/motivo.

### D6: API (`historiaclinica.api` + `infrastructure.EvolutionController`)
- `GET  /api/v1/patients/{patientId}/clinical-record/evolutions` → `200 EvolutionsResponse{entries[]}`.
- `POST /api/v1/patients/{patientId}/clinical-record/evolutions` body `EvolutionRequest{description, appointmentId?}` → `201 EvolutionEntryResponse`.
- `POST /api/v1/patients/{patientId}/clinical-record/evolutions/{entryId}/corrections` body `EvolutionCorrectionRequest{reason, description}` → `201 EvolutionEntryResponse`.
- `EvolutionEntryResponse{id, description, appointmentId, authorId, createdAt, correctsEntryId, correctionReason, correctedByEntryId}`.
- Bean Validation: `description` `@NotBlank @Size(max=5000)`; `reason` `@NotBlank @Size(max=500)`; `appointmentId` como `String` opcional con `@Pattern` de UUID (como C-17 con los enums): un `UUID` de Jackson mal formado caería en `HttpMessageNotReadableException` → `400 MALFORMED_REQUEST` de `ApiExceptionHandler`, sin indicar el campo; con `String` + `@Pattern` se obtiene `400 VALIDATION_FAILED` con `field = appointmentId` y sin eco. El controlador lo convierte a `UUID` tras validar.
- Request records **sin** `id`, `authorId`, `createdAt`, `clinicalRecordId`; el de corrección **sin** `appointmentId` (Jackson ignora desconocidos). `toString` sobrescritos sin texto clínico.
- Controlador con `@PreAuthorize("hasRole('DENTIST')")`, `Cache-Control: no-store`, `ClinicalRequestSupport`. Sin `PUT/PATCH/DELETE` → `405` en formato único (comportamiento existente).
- Seguridad: `/api/v1/**` ya exige `DENTIST` sin cambio de contraseña pendiente; `POST` requiere CSRF.

### D7: Auditoría y logging
- Reutiliza `AuditLogger` (fail-closed, misma transacción). `details` solo `entry_type` y, en correcciones, `corrects_entry_id`. **No** se agrega `appointment_id` a `details`: no es dato clínico, pero ampliar la allowlist no aporta (la fila de la evolución ya lo tiene) y mantiene `SensitiveDataSanitizer` intacto.
- Logs solo con IDs; ninguna excepción lleva descripción ni motivo.

### D8: Límites de módulos
- `ModuleBoundaries.ALLOWED_DEPENDENCIES`: `HISTORIA_CLINICA → {PACIENTES, AUDITORIA, AGENDA}`. Solo `historiaclinica.application` usa `agenda.api` (el `api` de `historiaclinica` no expone tipos de `agenda`; `appointmentId` es un `UUID`).
- KB 08 §reglas de dependencia se actualiza con la arista nueva (y `CHANGES.md` C-18 con V15) como parte del change.

### D9: Versión V15 y riesgo de colisión
- V15 es la siguiente libre en `dev-veronica` (V14 = `notification`, C-13). `master` llega a V13: si C-13 se mergea antes, el orden se mantiene. Re-verificar con `git ls-tree` sobre `origin/*` al inicio del apply y antes del merge; si V15 se ocupó, usar la siguiente libre y actualizar proposal, D1, spec `database-migrations` y tasks.
- Riesgo heredado (C-16 D9): versiones salteadas (V6, V9, V10) en otras ramas romperían BD locales ya migradas (Flyway sin `outOfOrder`) → recrear la BD local. Los tests usan BD embebida nueva.

### D10: Limpieza en tests
- La autorreferencia `corrects_entry_id` con `ON DELETE RESTRICT` impide borrar en una sola sentencia una cadena `A ← B ← C`. Helper `ClinicalFixtures.deleteEvolutions()` (rol de migración) que borra repetidamente las hojas (`WHERE id NOT IN (SELECT corrects_entry_id ... WHERE NOT NULL)`) hasta vaciar.
- Todo helper que borre `clinical_record`, `appointment` o pacientes con HC debe llamarlo antes. No se agrega configuración nueva (sin impacto en `MissingConfigurationTest`).

## Risks / Trade-offs

- **[Riesgo legal] PA-01 abierta** (Ley 27.706 sin relevar) → se heredan S-HC-01..07 de C-16 (firma = autor de sesión + instante del servidor; inmutabilidad por privilegios; conservación indefinida). Bloquea despliegue (C-28), no desarrollo. La evolución es el asiento más cercano a la "historia clínica" en sentido estricto de la Ley 26.529, por lo que la validación legal de S-HC-01 (firma) es más relevante aquí.
- **[Trade-off] Sin `performed_at`** (S-EV-01) → una atención cargada al día siguiente queda fechada al día de carga; el turno asociado da la fecha de atención. Agregarla luego es una migración aditiva (nullable).
- **[Trade-off] Turno no corregible** → si se asoció el turno equivocado, se corrige la entrada con motivo ("turno equivocado") y se registra una nueva con el turno correcto. Consistente con C-17 Q4.
- **[Riesgo] Consistencia paciente/turno solo en la app** → una inserción directa en la base podría asociar un turno de otro paciente; mitigado por un único punto de escritura y tests.
- **[Riesgo] Historial grande en un GET** → evoluciones por paciente en el orden de decenas/cientos; paginación diferida a C-22 si hace falta (Q7).
- **[Riesgo] Texto clínico en logs** → `toString` sobrescritos, validación sin eco, test de marcador (`EvolutionDataLeakageIT`).

## Migration Plan

1. Re-verificar que V15 esté libre en todas las ramas remotas.
2. Flyway aplica `V15__evolution_entry.sql` con el rol de migración.
3. `EvolutionPrivilegesIT` verifica que `${app_role}` solo tenga `SELECT`/`INSERT`.
4. Rollback: no hay rollback en caliente de datos clínicos; en entornos sin datos reales, recrear la BD. Nunca editar V15.

## Open Questions — resueltas (APROBADO por Veronica, 2026-11-22)

- **Q1 (texto)**: descripción de texto libre obligatoria, no vacía, **≤ 5000** caracteres; motivo ≤ 500 (convención C-16). **Aprobado.**
- **Q2 (S-EV-01, fecha)**: sin `performed_at`; la única fecha es `created_at` del servidor. **Aprobado**, como desvío documentado de KB 04 (se anota en KB 04 en la tarea 6.1).
- **Q3 (turno vinculable)**: mismo paciente; no `CANCELLED` ni `NO_SHOW`; inicio ≤ ahora (`CONFIRMED` ya comenzado o `COMPLETED`); varias evoluciones por turno; regla en la app (FK solo de existencia). **Aprobado tal como se propuso.**
- **Q4 (contrato de agenda)**: reutilizar `AppointmentNotificationLookup` sin cambios y habilitar `historiaclinica → agenda` (solo `api`). **Aprobado.**
- **Q5 (corrección)**: corrige `description`; conserva el turno de la original; cualquier entrada no corregida es corregible, incluidas correcciones (cadenas), por el mecanismo de C-16. **Aprobado.**
- **Q6 (S-EV-03)**: registrar una evolución nunca cambia el estado del turno. **Aprobado.**
- **Q7 (lectura)**: un único `GET` con todas las evoluciones, sin paginación ni filtro por turno. **Cambiado por la aprobación**: orden **más reciente primero** (`created_at DESC, id DESC`, desempate determinista por id; consistente con el historial del odontograma de C-17) en lugar del ascendente propuesto. Aplicado en D1 (índice), D2, D5, spec `clinical-evolutions` y tasks.
- **Q8 (códigos y auditoría)**: `422 EVOLUTION_APPOINTMENT_NOT_FOUND` (inexistente u otro paciente), `422 EVOLUTION_APPOINTMENT_NOT_ATTENDED`, `422 EVOLUTION_APPOINTMENT_NOT_STARTED`; auditoría `HC_READ`/`HC_CREATE`/`HC_CORRECT` con `entity_type = evolution_entry`. **Aprobado.**
- **Q9 (herencia C-16)**: S-HC-01..07, sin trigger anti-UPDATE para el rol de migración, lectura de inactivos permitida, escritura `409 PATIENT_INACTIVE`; PA-01 sigue abierta. **Aprobado.**
