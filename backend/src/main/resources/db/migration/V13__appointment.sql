-- C-11 appointments-core. Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Numero V13 (no V8 como decia CHANGES.md): V8 es schedule_block (C-09) y V12 es odontogram_entry (C-17); Flyway
-- corre con outOfOrder=false, asi que la unica version valida es la siguiente libre. No se tocan V1-V12.
-- Crea la tabla de turnos (US-001..US-004, RN-AG-01, RN-AG-04). El rango period se genera en la base desde
-- starts_at/ends_at con limites semiabiertos '[)' (mismo patron que V8). btree_gist ya fue creado en V1.
-- La exclusion cubre turno-turno; turno-bloqueo se serializa con un lock por consultorio en la aplicacion.

CREATE TABLE appointment (
    id                    uuid        PRIMARY KEY,
    practice_id           uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    patient_id            uuid        NOT NULL REFERENCES patient (id) ON DELETE RESTRICT,
    service_id            uuid        NOT NULL REFERENCES service (id) ON DELETE RESTRICT,
    starts_at             timestamptz NOT NULL,
    ends_at               timestamptz NOT NULL,
    duration_min_snapshot integer     NOT NULL,
    status                text        NOT NULL DEFAULT 'CONFIRMED',
    origin                text        NOT NULL,
    overbooking           boolean     NOT NULL DEFAULT false,
    cancelled_by          text,
    cancelled_at          timestamptz,
    notes                 text,
    period                tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT appointment_status_check
        CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'NO_SHOW')),
    CONSTRAINT appointment_origin_check CHECK (origin IN ('PUBLIC', 'INTERNAL')),
    CONSTRAINT appointment_cancelled_by_check CHECK (cancelled_by IN ('PATIENT', 'DENTIST')),
    CONSTRAINT appointment_duration_check CHECK (duration_min_snapshot > 0),
    CONSTRAINT appointment_notes_check CHECK (char_length(notes) <= 1000),
    CONSTRAINT appointment_cancellation_check CHECK (
        (status = 'CANCELLED' AND cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_by IS NULL AND cancelled_at IS NULL)),
    CONSTRAINT appointment_period_check CHECK (ends_at > starts_at),
    CONSTRAINT appointment_no_overlap
        EXCLUDE USING gist (practice_id WITH =, period WITH &&) WHERE (status <> 'CANCELLED')
);

CREATE INDEX appointment_practice_starts_idx ON appointment (practice_id, starts_at);
CREATE INDEX appointment_patient_idx ON appointment (patient_id);
CREATE INDEX appointment_service_idx ON appointment (service_id);
CREATE INDEX appointment_status_starts_idx ON appointment (status, starts_at);

-- Un turno solo cambia de estado: sin borrado fisico (como patient y service). SELECT/INSERT/UPDATE se conservan.
REVOKE DELETE, TRUNCATE ON appointment FROM ${app_role};
