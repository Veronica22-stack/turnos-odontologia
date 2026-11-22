# Proposal: C-17 `odontogram`

## Why

El odontólogo necesita documentar el estado de cada pieza dental de sus pacientes (US-061) como parte de la historia clínica, con el mismo régimen legal que la anamnesis: asientos inmutables, fechados, con autor y corregibles solo mediante una nueva entrada que referencia la original (RN-HC-01, RN-HC-02, Ley 26.529). C-16 ya dejó el núcleo inmutable (`clinical_record`, convención de columnas y `ClinicalCorrections` genérico); este change agrega el segundo tipo de entrada clínica reutilizándolo sin reinventarlo.

Governance: **CRÍTICO** (datos de salud, Leyes 25.326 y 26.529; PA-01/Ley 27.706 sigue abierta y los supuestos S-HC-01..08 de C-16 aplican igual). Ningún código se escribe sin aprobación explícita de este proposal/design (preguntas Q1–Q6 en `design.md`).

## What Changes

- **Migración `V12__odontogram_entry.sql`** (número reservado por `CHANGES.md`, verificado libre en todas las ramas remotas al momento del propose):
  - Tabla `odontogram_entry` con la convención de corrección de C-16 (`clinical_record_id`, `corrects_entry_id` + `correction_reason` ambos o ninguno, motivo no vacío ≤ 500, FK compuesta a la misma HC, `UNIQUE (corrects_entry_id)`, `author_id`, `created_at` del servidor).
  - Columnas propias: `tooth_code` (FDI, permanente 11–48 y temporal 51–85), `surface` opcional (catálogo de caras), `condition` (catálogo base) y `notes` opcional.
  - `CHECK` de notación FDI, de catálogo de estados y de caras en la base (no solo en la app).
  - `REVOKE UPDATE, DELETE, TRUNCATE` al rol `${app_role}`.
- **Odontograma en `historiaclinica`** (reutiliza C-16):
  - Nuevo tipo `ClinicalEntryType.ODONTOGRAM` (`odontogram` / `odontogram_entry`).
  - Entidad `OdontogramEntry extends ClinicalEntry`; value objects `ToothCode` (FDI), `ToothSurface`, `ToothCondition`.
  - `OdontogramCorrectionStore implements CorrectableEntryStore` (solo es corregible la entrada vigente de su posición) y uso de `ClinicalCorrections.correct(...)`.
  - **Estado actual derivado**: por cada posición (pieza + cara), la entrada más reciente que nadie corrigió; el historial completo se conserva y marca `correctedByEntryId` (RN-HC-07).
- **Endpoints REST (solo `DENTIST`, deny-by-default, CSRF en `POST`)**:
  - `GET /api/v1/patients/{patientId}/clinical-record/odontogram` — estado actual derivado + historial completo.
  - `POST /api/v1/patients/{patientId}/clinical-record/odontogram` — registra 1..64 estados (una entrada por posición) en una transacción; abre la HC en el primer registro (PA-15).
  - `POST /api/v1/patients/{patientId}/clinical-record/odontogram/{entryId}/corrections` — corrección con motivo obligatorio por la ruta uniforme de C-16.
  - **No existen** `PUT`/`PATCH`/`DELETE`.
- **Auditoría** (reutiliza `AuditLogger` fail-closed y la allowlist de C-16, sin cambiarla): `HC_READ` por lectura, un `HC_CREATE` por entrada creada, `HC_CORRECT` por corrección; `details` solo `entry_type` y `corrects_entry_id`. Nunca pieza, estado, notas ni motivo.

### Supuestos propios de este change (PA-17, a confirmar con el profesional)

- **S-OD-01 Notación**: FDI (ISO 3950) de dos dígitos; se aceptan dentición permanente (cuadrantes 1–4, piezas 1–8) y temporal (cuadrantes 5–8, piezas 1–5) en el mismo odontograma (dentición mixta).
- **S-OD-02 Catálogo base** (KB 04): `HEALTHY` (sano), `CARIES`, `FILLED` (obturado), `MISSING` (ausente), `CROWN` (corona), `ROOT_CANAL` (endodoncia). Ampliarlo requiere una migración nueva.
- **S-OD-03 Caras**: opcionales; catálogo mínimo `OCCLUSAL` (oclusal/incisal), `MESIAL`, `DISTAL`, `VESTIBULAR`, `LINGUAL` (lingual/palatino). Solo `CARIES` y `FILLED` admiten cara; los demás estados son de pieza completa.

## Capabilities

### New Capabilities
- `clinical-odontogram`: registro del estado de piezas dentales en notación FDI con catálogo base, estado actual derivado, historial, corrección por la ruta uniforme y auditoría de lectura/escritura.

### Modified Capabilities
- `database-migrations`: agrega la migración V12 con `odontogram_entry`, sus restricciones de dominio y de corrección, y la revocación de UPDATE/DELETE/TRUNCATE al rol de aplicación.

(Las capacidades `clinical-record`, `audit-log` y `api-access-policy` ya están formuladas de forma genérica para todo tipo de entrada y toda ruta `/clinical-record/**`; no cambian sus requisitos.)

## Impact

- **Base de datos**: nueva `V12__odontogram_entry.sql`. Sin cambios en migraciones aplicadas.
- **Backend** (`historiaclinica`): `ClinicalEntryType` (+1 constante), dominio del odontograma, repositorio + adaptador Spring Data, `OdontogramService`, `OdontogramCorrectionStore`, DTO records, `OdontogramController`, mapeos en `ClinicalRequestSupport` (o un helper propio). Sin cambios en `pacientes`, `auditoria`, `SecurityConfig` ni `SensitiveDataSanitizer`.
- **Tests**: JUnit 5 + PostgreSQL embebido (zonky), Strict TDD: esquema y privilegios del rol app, FDI válido/inválido, catálogo, estado derivado, historial, corrección, inmutabilidad, auditoría, no filtración en logs, seguridad.
- **Desbloquea**: la parte de odontograma de C-22 (`clinical-record-ui`).
- **Riesgo legal**: el mismo de C-16 (PA-01 abierta); bloquea despliegue (C-28), no desarrollo.
