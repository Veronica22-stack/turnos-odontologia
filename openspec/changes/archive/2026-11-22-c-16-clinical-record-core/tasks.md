# Tasks: C-16 `clinical-record-core`

> Governance **CRÍTICO**: no iniciar el apply sin aprobación explícita de proposal/design (Open Questions Q1–Q5 de `design.md`). Las tareas asumen los valores por defecto de esas preguntas.
> **Strict TDD**: cada tarea es RED (test que falla) → GREEN (mínimo código) → TRIANGULATE → REFACTOR. Unitarios con `./gradlew test`; integración (PostgreSQL embebido zonky, `AbstractIntegrationTest`) con `./gradlew integrationTest`. Safety net antes de tocar archivos existentes: `./gradlew check` en verde.
> Commits (solo a pedido del usuario): `feat(C-16): ...` / `test(C-16): ...`.

## 1. Migración V11 e inmutabilidad en la base

- [x] 1.1 RED: escribir `database/ClinicalRecordSchemaIT` (existencia de `clinical_record`/`anamnesis_entry`, `UNIQUE (patient_id)`, `UNIQUE (clinical_record_id, version)`, CHECK de `content` objeto, par `corrects_entry_id`/`correction_reason`, motivo vacío rechazado, FK compuesta que impide corregir una entrada de otra HC, `UNIQUE (corrects_entry_id)`) y `database/ClinicalRecordPrivilegesIT` (con la conexión del rol app: `UPDATE`, `DELETE` y `TRUNCATE` fallan en ambas tablas; `INSERT`/`SELECT` funcionan), siguiendo el patrón de `AuditLogPrivilegesIT`; verificar que fallan por ausencia de tablas
- [x] 1.2 GREEN: crear `backend/src/main/resources/db/migration/V11__clinical_record.sql` según design D2 (sin editar migraciones previas; confirmar antes que V11 sigue libre en `master` y ramas remotas) y verificar `./gradlew integrationTest --tests "*ClinicalRecordSchemaIT" --tests "*ClinicalRecordPrivilegesIT" --tests "*MigrationsIT"` en verde

## 2. Puerto de pacientes para otros módulos

- [x] 2.1 RED/GREEN: escribir `pacientes/infrastructure/PatientLookupAdapterIT` (activo → `active=true`; dado de baja → `active=false`; inexistente o de otra instalación → vacío; no se registra `PATIENT_READ`) y luego crear `pacientes.api.PatientLookup` + `PatientStatus(id, active)` y el adaptador en `pacientes.infrastructure` (design D8); verificar el IT y `ProductionArchitectureTest` en verde

## 3. Dominio de la historia clínica

- [x] 3.1 RED/GREEN/TRIANGULATE: `CorrectionReason` con `CorrectionReasonTest` (nulo, vacío, solo espacios y > 500 rechazados; recorta espacios; 500 exactos aceptado; `toString` sin el texto); verificar `./gradlew test --tests "*CorrectionReasonTest"`
- [x] 3.2 RED/GREEN: `AnamnesisContent` (record con `schemaVersion`, 5 campos ≤ 2000, al menos uno no vacío, `toString` sin contenido) con `AnamnesisContentTest` (todos vacíos rechazado, campo excedido rechazado, un solo campo aceptado, `toString` no contiene el texto)
- [x] 3.3 RED/GREEN: `ClinicalEntry` (`@MappedSuperclass`, `@Immutable`, columnas `updatable=false`, sin setters), `EntryProvenance`, `Correction`, `ClinicalEntryType.ANAMNESIS`, `ClinicalRecord` y `AnamnesisEntry` con `AnamnesisEntryTest` (alta sin corrección; corrección lleva id original + motivo; autor e instante obligatorios; versión ≥ 1); verificar también `ProductionArchitectureTest` (dominio sin Spring)
- [x] 3.4 RED/GREEN: repositorios de dominio (`ClinicalRecordRepository` con `openIfAbsent` vía `INSERT ... ON CONFLICT DO NOTHING`, `AnamnesisEntryRepository` como `CorrectableEntryStore`) y adaptadores Spring Data en `infrastructure`, con `historiaclinica/infrastructure/ClinicalRecordRepositoryIT` (apertura idempotente; dos aperturas concurrentes → una sola HC; listado de anamnesis por HC; `isCorrected`; `max(version)`); verificar el IT en verde

## 4. Casos de uso: apertura, anamnesis y correcciones genéricas

- [x] 4.1 RED/GREEN/TRIANGULATE: `ClinicalRecordService` (apertura al primer registro, resumen, validación de paciente vía `PatientLookup`) con `ClinicalRecordServiceTest` usando `Clock` fijo y fakes: lectura sin HC no la abre y audita `HC_READ`; paciente inexistente → `PATIENT_NOT_FOUND` sin auditoría; inactivo: lectura OK, escritura → `PATIENT_INACTIVE`
- [x] 4.2 RED/GREEN/TRIANGULATE: `AnamnesisService.register` y `.read` con `AnamnesisServiceTest`: versión 1 abre la HC; versión 2 no altera la 1; autor = actor y `createdAt` = `Clock`; `HC_CREATE` con `entity_id` de la nueva entrada y `details` solo `{entryType, version}`; historial descendente con `correctedByEntryId`; violación de `record_version_unique` → `CLINICAL_RECORD_CONCURRENT_WRITE`
- [x] 4.3 RED/GREEN/TRIANGULATE: `ClinicalCorrections.correct` genérico (design D4) con `ClinicalCorrectionsTest` sobre un store fake: corrección válida crea entrada con `correctsEntryId` + motivo y audita `HC_CORRECT` con `details` `{entryType, correctsEntryId}` sin el motivo; entrada inexistente/de otra HC → `CLINICAL_ENTRY_NOT_FOUND`; ya corregida → `CLINICAL_ENTRY_ALREADY_CORRECTED`; hook `assertCorrectable` invocado; motivo inválido no persiste ni audita
- [x] 4.4 RED/GREEN: `AnamnesisCorrectionStore.assertCorrectable` (solo la vigente) con test: corregir la vigente v3 crea v4 que referencia v3; corregir v1 con v2 vigente → `CLINICAL_ENTRY_NOT_CURRENT`
- [x] 4.5 RED/GREEN: fail-closed transaccional con `historiaclinica/application/ClinicalAuditFailureIT` (`AuditLogger` que falla): alta y corrección no dejan HC ni entrada nuevas; lectura no entrega datos; violación concurrente de `corrects_once_unique` → `CLINICAL_ENTRY_ALREADY_CORRECTED`

## 5. API REST, seguridad y auditoría de punta a punta

- [x] 5.1 RED/GREEN: DTO records en `historiaclinica.api` (`AnamnesisRequest`, `AnamnesisCorrectionRequest{reason, content}`, `AnamnesisEntryResponse`, `AnamnesisResponse`, `ClinicalRecordSummaryResponse`) con Bean Validation y `toString` sin contenido, verificados con `ClinicalRecordRequestValidationTest` (motivo ausente/vacío/>500, campo >2000, anamnesis vacía)
- [x] 5.2 RED/GREEN: `ClinicalRecordController` y `AnamnesisController` (`GET .../clinical-record`, `GET|POST .../anamnesis`, `POST .../anamnesis/{entryId}/corrections`; `RequestContext` con IP/UA; actor desde la sesión) con `historiaclinica/infrastructure/ClinicalRecordEndpointsIT`: 201/200 según specs; body con `authorId`/`createdAt`/`version` falsos ignorados; `PUT`/`PATCH`/`DELETE` → 405 en formato único sin cambios; códigos 404/409 de specs; `Cache-Control: no-store`
- [x] 5.3 RED/GREEN: seguridad y auditoría en `ClinicalRecordEndpointsIT`: sin sesión → 401 sin `HC_READ`; sin CSRF → 403 `CSRF_REQUIRED` sin entrada; cambio de contraseña pendiente → 403 `PASSWORD_CHANGE_REQUIRED` sin auditoría; filas `HC_READ`/`HC_CREATE`/`HC_CORRECT` con `user_id`, `patient_id`, `ip`, `entity_id` correctos; validación fallida no audita
- [x] 5.4 RED/GREEN: test de no filtración (`ClinicalDataLeakageIT`, patrón de `AuditLogLeakageIT`): con un marcador único en contenido y motivo, ni los logs capturados, ni las respuestas de error, ni `audit_log.details` contienen el marcador

## 6. Arquitectura y verificación integral

- [x] 6.1 Verificar `ModuleBoundariesRulesTest` y `ProductionArchitectureTest` en verde (`historiaclinica` solo usa `pacientes.api` y `auditoria.api`; `application` sin `api`/`infrastructure` propios)
- [x] 6.2 Ejecutar `./gradlew check` completo en verde y completar la tabla "TDD Cycle Evidence" en el resumen del apply

## Workflow follow-up

- Aprobación humana de Q1–Q5 (`design.md`) antes del apply (governance CRÍTICO).
- Archivar C-10 antes que C-16 (el delta `patients-management` asume su spec principal).
- Validación legal de los supuestos S-HC-01..08 (PA-01, PA-11, PA-15, PA-18) antes de C-28; actualizar `knowledge-base/05` (RN-HC) y `11` con el resultado.
- `verify` y `/opsx:archive` tras el apply.
