# Proposal: C-18 `evolutions`

## Why

El odontólogo necesita registrar lo realizado en cada atención para seguir el tratamiento del paciente (US-062), opcionalmente vinculado al turno en el que se hizo, con el mismo régimen legal que la anamnesis y el odontograma: asientos inmutables, fechados, con autor y corregibles solo mediante una entrada nueva que referencia la original (RN-HC-01, RN-HC-02, RN-HC-05, Ley 26.529). C-16 dejó el núcleo inmutable reutilizable y C-17 demostró que un tipo nuevo se agrega sin tocarlo; C-11 dejó los turnos y una consulta de solo lectura por la `api` de `agenda`. Este change agrega el tercer (y último del MVP) tipo de entrada clínica.

Governance: **CRÍTICO** (datos de salud, Leyes 25.326 y 26.529; PA-01/Ley 27.706 sigue abierta y los supuestos S-HC-01..07 de C-16 aplican igual). Plan **APROBADO por Veronica el 2026-11-22** (preguntas Q1–Q9 resueltas en `design.md`; Q7 cambiada a "más reciente primero").

## What Changes

- **Migración `V15__evolution_entry.sql`** (`CHANGES.md` dice V13, pero V13 es `appointment` y V14 `notification` en `dev-veronica`; la última versión en `master` es V13. **Re-verificar contra `master` y todas las ramas remotas al inicio del apply**):
  - Tabla `evolution_entry` con la convención de corrección de C-16 (`clinical_record_id`, `corrects_entry_id` + `correction_reason` ambos o ninguno, motivo no vacío ≤ 500, FK compuesta a la misma HC, `UNIQUE (corrects_entry_id)`, `author_id`, `created_at` del servidor).
  - Columnas propias: `description` (texto libre obligatorio, no vacío, ≤ 5000) y `appointment_id` opcional con FK a `appointment` (`ON DELETE RESTRICT`).
  - `REVOKE UPDATE, DELETE, TRUNCATE` al rol `${app_role}`.
- **Evoluciones en `historiaclinica`** (reutiliza C-16 sin modificarlo):
  - Nuevo tipo `ClinicalEntryType.EVOLUTION` (`evolutions` / `evolution_entry`).
  - Entidad `EvolutionEntry extends ClinicalEntry`, value object `EvolutionDescription`, repositorio + adaptador, `EvolutionCorrectionStore implements CorrectableEntryStore` y uso de `ClinicalCorrections.correct(...)`.
  - **Asociación a turno** validada por la `api` de `agenda` (`AppointmentNotificationLookup`, ya existente, sin cambios): el turno debe existir, ser **del mismo paciente**, no estar `CANCELLED` ni `NO_SHOW` y haber comenzado.
  - Historial **más reciente primero** (`created_at DESC, id DESC`, desempate determinista; mismo criterio que el historial de C-17), con `correctsEntryId` y `correctedByEntryId` por entrada (RN-HC-07).
- **Endpoints REST (solo `DENTIST`, deny-by-default, CSRF en `POST`)**:
  - `GET /api/v1/patients/{patientId}/clinical-record/evolutions` — todas las evoluciones del paciente, de la más reciente a la más antigua.
  - `POST /api/v1/patients/{patientId}/clinical-record/evolutions` — registra una evolución (`description`, `appointmentId?`); abre la HC en el primer registro (PA-15).
  - `POST /api/v1/patients/{patientId}/clinical-record/evolutions/{entryId}/corrections` — corrección con motivo obligatorio por la ruta uniforme de C-16; conserva el turno de la original.
  - **No existen** `PUT`/`PATCH`/`DELETE`.
- **Auditoría** (reutiliza `AuditLogger` fail-closed y la allowlist de C-16, sin cambiarla): `HC_READ` por lectura, `HC_CREATE` por alta, `HC_CORRECT` por corrección; `entity_type = evolution_entry`; `details` solo `entry_type` y `corrects_entry_id`. Nunca descripción, motivo ni turno.
- **Límites de módulos**: se habilita la dependencia `historiaclinica` → `agenda` (solo por su `api`). `agenda` no conoce a `historiaclinica`; no se forma ciclo.

### Supuestos propios de este change (a confirmar)

- **S-EV-01 Fecha clínica**: la fecha de la evolución es el `created_at` del servidor ("autor y fecha automáticos", US-062). No se agrega `performed_at` (KB 04 lo lista); si la atención se carga tarde, la fecha clínica de referencia es la del turno asociado. Agregarlo después es una migración nueva con columna nullable.
- **S-EV-02 Texto libre**: una evolución es una descripción de texto libre (sin estructura por prestación ni vínculo al odontograma).
- **S-EV-03 Turno no se modifica**: registrar una evolución **no** cambia el estado del turno (no lo marca `COMPLETED`); el resultado del turno sigue siendo una acción explícita de `agenda`.

## Capabilities

### New Capabilities
- `clinical-evolutions`: registro de evoluciones de texto libre con asociación opcional a un turno del mismo paciente, historial cronológico, corrección por la ruta uniforme, inmutabilidad y auditoría de lectura/escritura.

### Modified Capabilities
- `database-migrations`: agrega la migración V15 con `evolution_entry`, sus restricciones de texto y de corrección, la FK opcional a `appointment` y la revocación de UPDATE/DELETE/TRUNCATE al rol de aplicación.
- `module-boundaries`: el grafo permitido agrega `historiaclinica` → `agenda` (solo `api`).

(`clinical-record`, `audit-log` y `api-access-policy` ya están formuladas de forma genérica para todo tipo de entrada y toda ruta `/clinical-record/**`; `appointments` ya ofrece la consulta de turno "a otros módulos". No cambian sus requisitos.)

## Impact

- **Base de datos**: nueva `V15__evolution_entry.sql`. Sin cambios en migraciones aplicadas. Nueva FK de `evolution_entry` hacia `appointment` (los turnos ya no se borran físicamente: V13 revoca `DELETE`).
- **Backend**:
  - `historiaclinica`: `ClinicalEntryType` (+1 constante), dominio de evoluciones, repositorio + adaptador Spring Data, `EvolutionService`, `EvolutionCorrectionStore`, `EvolutionAppointmentGuard` (consume `agenda.api`), DTO records, `EvolutionController`, mapeo en `ClinicalRequestSupport` (o helper propio).
  - Sin cambios en `agenda`, `pacientes`, `auditoria`, `SecurityConfig`, `SensitiveDataSanitizer`, `ClinicalCorrections` ni `ClinicalWriteConflicts`.
  - Tests de arquitectura: `ModuleBoundaries.ALLOWED_DEPENDENCIES` agrega `AGENDA` a `HISTORIA_CLINICA`; KB 08 se actualiza con la nueva arista.
- **Tests**: JUnit 5 + PostgreSQL embebido (zonky), Strict TDD: esquema y privilegios, dominio, asociación a turno (mismo paciente, estados, futuro), orden, corrección, inmutabilidad, auditoría, no filtración en logs, seguridad. Los helpers de limpieza que borran `appointment` o `clinical_record` deben borrar antes `evolution_entry`.
- **Desbloquea**: la parte de evoluciones de C-22 (`clinical-record-ui`, que además requiere C-17 y C-21).
- **Riesgo legal**: el mismo de C-16 (PA-01 abierta); bloquea despliegue (C-28), no desarrollo.
