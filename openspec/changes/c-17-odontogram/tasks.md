# Tasks: C-17 `odontogram`

> Governance **CRÍTICO**: no iniciar el apply sin aprobación explícita de proposal/design (Q1–Q7 de `design.md`). Las tareas asumen los valores por defecto de esas preguntas.
> **Strict TDD**: cada tarea es RED (test que falla) → GREEN (mínimo código) → TRIANGULATE → REFACTOR. Unitarios con `./gradlew test`; integración (PostgreSQL embebido zonky, `AbstractIntegrationTest`) con `./gradlew integrationTest`. Safety net antes de tocar archivos existentes (`ClinicalEntryType`, `ClinicalRequestSupport`): `./gradlew check` en verde y anotar el baseline.
> Reutilizar C-16 (`ClinicalEntry`, `CorrectableEntryStore`, `ClinicalCorrections`, `ClinicalRecordService.openForWrite`, `ClinicalPatientGuard`, `RequestContext`, `ClinicalWriteConflicts`) **sin modificarlos**; si algo obliga a cambiarlos, detenerse y reportar.
> Commits (solo a pedido del usuario): `feat(C-17): ...` / `test(C-17): ...`.

## 1. Migración V12 e inmutabilidad en la base

- [ ] 1.1 RED: escribir `database/OdontogramSchemaIT` (tabla existe; FDI válidos `11`,`48`,`51`,`85` aceptados e inválidos `19`,`56`,`59`,`90`,`00`,`1`,`111` rechazados; estado fuera de catálogo rechazado; cara fuera de catálogo rechazada; `MISSING` con cara rechazado; notas > 500 rechazadas; par `corrects_entry_id`/`correction_reason`; motivo vacío; FK compuesta impide corregir entrada de otra HC; `UNIQUE (corrects_entry_id)`) y `database/OdontogramPrivilegesIT` (rol app: `UPDATE`, `DELETE`, `TRUNCATE` fallan; `INSERT`/`SELECT` funcionan), patrón de `ClinicalRecordSchemaIT`/`ClinicalRecordPrivilegesIT`; verificar que fallan por ausencia de la tabla
- [ ] 1.2 GREEN: re-verificar con `git ls-tree` que V12 sigue libre en `origin/*`; crear `backend/src/main/resources/db/migration/V12__odontogram_entry.sql` según design D1 (sin editar migraciones previas) y verificar `./gradlew integrationTest --tests "*OdontogramSchemaIT" --tests "*OdontogramPrivilegesIT" --tests "*MigrationsIT"` en verde

## 2. Dominio del odontograma

- [ ] 2.1 RED/GREEN/TRIANGULATE: `ToothCode` con `ToothCodeTest` (bordes válidos 11, 18, 41, 48, 51, 55, 81, 85; inválidos 10, 19, 49, 56, 86, 90, 00, nulo, vacío, 1 y 3 dígitos, no numérico; `dentition()` PERMANENT/TEMPORARY); verificar `./gradlew test --tests "*ToothCodeTest"`
- [ ] 2.2 RED/GREEN: `ToothCondition` (6 estados, `allowsSurface()` solo `CARIES`/`FILLED`), `ToothSurface` (5 caras) y `ToothPosition` con `ToothConditionTest` (cada estado y su admisión de cara; posición con y sin cara distinguibles y con igualdad por valor)
- [ ] 2.3 RED/GREEN: `ClinicalEntryType.ODONTOGRAM("odontogram","odontogram_entry")` y `OdontogramEntry extends ClinicalEntry` (`@Immutable`, `updatable=false`, sin setters, fábricas `register`/`correct`) con `OdontogramEntryTest` (alta sin corrección; corrección copia pieza y cara de la original y lleva id + motivo; cara con estado de pieza completa rechazada; notas > 500 rechazadas; `toString` sin notas ni motivo); verificar también `ProductionArchitectureTest` (dominio sin Spring)
- [ ] 2.4 RED/GREEN/TRIANGULATE: `OdontogramState.derive` (función pura, design D2) con `OdontogramStateTest`: entrada nueva reemplaza a la anterior en la misma posición; posiciones (pieza completa / cada cara) independientes; entrada corregida excluida del estado actual y su corrección incluida; historial completo descendente con `correctedByEntryId`; lista vacía → estado vacío; pieza sin registros ausente; desempate por id con igual `createdAt`
- [ ] 2.5 RED/GREEN: `OdontogramEntryRepository` (`CorrectableEntryStore` + `findNewestFirst` + `existsNewerAtPosition`) y adaptador Spring Data en `infrastructure` con `historiaclinica/infrastructure/OdontogramRepositoryIT` (alta y lectura por HC en orden `created_at DESC, id DESC`; `findInRecord` no encuentra entradas de otra HC ni de anamnesis; `isCorrected`; `existsNewerAtPosition` con y sin cara); verificar el IT en verde

## 3. Casos de uso: alta, lectura y corrección

- [ ] 3.1 RED/GREEN/TRIANGULATE: `OdontogramService.register` con `OdontogramServiceTest` (`Clock` fijo y fakes de `ClinicalFakes`): primer registro abre la HC; lote de N crea N entradas con autor = actor y `createdAt` = `Clock`; un `HC_CREATE` por entrada con `entity_type=odontogram_entry`, `entity_id` propio y `details` solo `{entry_type}`; paciente inactivo → `PATIENT_INACTIVE` sin entradas ni auditoría; paciente inexistente → `PATIENT_NOT_FOUND`
- [ ] 3.2 RED/GREEN: `OdontogramService.read` con `OdontogramServiceTest`: sin HC → vacío, no abre HC, un `HC_READ`; con HC → `current` e `history` de `OdontogramState`; paciente inactivo legible; paciente inexistente → `PATIENT_NOT_FOUND` sin auditoría
- [ ] 3.3 RED/GREEN/TRIANGULATE: `OdontogramCorrectionStore.assertCorrectable` + `OdontogramService.correct` vía `ClinicalCorrections` con `OdontogramCorrectionTest`: corregir la vigente crea entrada en la misma posición con `correctsEntryId` + motivo y audita `HC_CORRECT` `{entry_type, corrects_entry_id}` sin motivo; corregir una superada → `CLINICAL_ENTRY_NOT_CURRENT`; ya corregida → `CLINICAL_ENTRY_ALREADY_CORRECTED`; id de anamnesis o de otra HC → `CLINICAL_ENTRY_NOT_FOUND`; estado incompatible con la cara → `ODONTOGRAM_SURFACE_NOT_ALLOWED` sin persistir ni auditar; motivo inválido no persiste ni audita
- [ ] 3.4 RED/GREEN: fail-closed transaccional en `historiaclinica/application/OdontogramAuditFailureIT` (patrón `ClinicalAuditFailureIT`, `AuditLogger` que falla): lote y corrección no dejan HC ni entradas; lectura no entrega datos; dos correcciones concurrentes de la misma entrada → una `201` y otra `409 CLINICAL_ENTRY_ALREADY_CORRECTED` (traducción por `corrects_once_unique`)

## 4. API REST, seguridad y auditoría de punta a punta

- [ ] 4.1 RED/GREEN: DTO records en `historiaclinica.api` (`OdontogramRequest`, `OdontogramEntryRequest`, `OdontogramCorrectionRequest`, `OdontogramEntryResponse`, `OdontogramEntriesResponse`, `OdontogramResponse`) con Bean Validation y constraints de clase `@SurfaceAllowedForCondition` y `@DistinctToothPositions`, `toString` sin notas ni motivo, verificados con `OdontogramRequestValidationTest` (lote vacío y de 65; FDI inválido; estado/cara fuera de catálogo; cara en estado de pieza completa; posición repetida; notas > 500; motivo ausente/vacío/> 500)
- [ ] 4.2 RED/GREEN: `OdontogramController` (`GET|POST .../odontogram`, `POST .../odontogram/{entryId}/corrections`; actor de la sesión; `RequestContext`; `Cache-Control: no-store`) con `historiaclinica/infrastructure/OdontogramEndpointsIT`: `201`/`200` según spec; estado derivado tras alta, nueva alta y corrección; body con `authorId`/`createdAt`/`id` ignorados; corrección con `toothCode`/`surface` ignorados; `PUT`/`PATCH`/`DELETE` → `405` en formato único sin cambios; códigos `400`/`404`/`409`/`422` de la spec sin eco de valores
- [ ] 4.3 RED/GREEN: seguridad y auditoría en `OdontogramEndpointsIT`: sin sesión `GET` → `401` sin `HC_READ`; `POST` sin CSRF → `403 CSRF_REQUIRED` sin entradas; cambio de contraseña pendiente → `403 PASSWORD_CHANGE_REQUIRED` sin auditoría; filas `HC_READ`/`HC_CREATE` (una por entrada)/`HC_CORRECT` con `user_id`, `patient_id`, `ip`, `entity_type=odontogram_entry`, `entity_id` correctos; validación fallida no audita
- [ ] 4.4 RED/GREEN: `OdontogramDataLeakageIT` (patrón `ClinicalDataLeakageIT`): con un marcador único en notas y motivo, ni los logs capturados, ni las respuestas de error, ni `audit_log.details` contienen el marcador ni la pieza/estado

## 5. Verificación integral

- [ ] 5.1 Verificar `ModuleBoundariesRulesTest` y `ProductionArchitectureTest` en verde (`historiaclinica` solo usa `pacientes.api` y `auditoria.api`; `application` sin `api`/`infrastructure` propios) y que `ClinicalCorrections`, `ClinicalWriteConflicts`, `SensitiveDataSanitizer` y `SecurityConfig` no cambiaron (`git diff --stat`)
- [ ] 5.2 Ejecutar `./gradlew check` completo en verde (comparar con el baseline) y completar la tabla "TDD Cycle Evidence" en el resumen del apply

## Workflow follow-up

- Aprobación humana de Q1–Q7 (`design.md`) antes del apply (governance CRÍTICO).
- Confirmar con el profesional PA-17 (catálogo, caras, dentición) y actualizar `knowledge-base/04` §odontogram_entry y `10` §PA-17 con el resultado.
- Validación legal de S-HC-01..08 heredados (PA-01) antes de C-28.
- `verify` y `/opsx:archive` tras el apply.
