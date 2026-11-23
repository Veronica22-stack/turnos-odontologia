-- C-18 evolutions.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Por que V15: V14 es notification (C-13) en esta rama y ninguna rama remota ocupa V15; CHANGES.md reservaba V13,
-- que ya esta usada por appointment. No se edita ninguna migracion anterior (V1-V14).
-- Evoluciones clinicas append-only: cada entrada es el asiento de una atencion (descripcion libre) y puede asociarse
-- a un turno; una correccion es otra entrada que referencia a la original. La inmutabilidad se garantiza revocando
-- UPDATE/DELETE/TRUNCATE al rol de la aplicacion (misma convencion que anamnesis_entry V11 y odontogram_entry V12).
-- Desvio respecto de KB 04: no hay performed_at; la unica fecha es created_at, fijada por el servidor (S-EV-01).
-- La regla "el turno es del mismo paciente y ya comenzio/no esta cancelado" se valida en la aplicacion; la FK solo
-- garantiza que el turno exista (mismo criterio que notification, V14).

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
    -- Convencion de correccion de C-16 (el sufijo corrects_once_unique lo traduce a 409 la aplicacion).
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
CREATE INDEX evolution_entry_appointment_idx ON evolution_entry (appointment_id) WHERE appointment_id IS NOT NULL;
CREATE INDEX evolution_entry_author_idx ON evolution_entry (author_id);

REVOKE UPDATE, DELETE, TRUNCATE ON evolution_entry FROM ${app_role};
