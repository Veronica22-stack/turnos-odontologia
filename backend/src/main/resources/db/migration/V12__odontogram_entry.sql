-- C-17 odontogram.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Odontograma append-only: cada entrada es el estado de una pieza (FDI) o de una cara de pieza en un instante; una
-- correccion es otra entrada que referencia a la original. La inmutabilidad se garantiza revocando
-- UPDATE/DELETE/TRUNCATE al rol de la aplicacion (misma convencion que anamnesis_entry, V11).

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
    -- FDI: permanente 11-18..41-48, temporal 51-55..81-85.
    CONSTRAINT odontogram_entry_tooth_code_check CHECK (tooth_code ~ '^([1-4][1-8]|[5-8][1-5])$'),
    CONSTRAINT odontogram_entry_condition_check
        CHECK (condition IN ('HEALTHY', 'CARIES', 'FILLED', 'MISSING', 'CROWN', 'ROOT_CANAL')),
    CONSTRAINT odontogram_entry_surface_check
        CHECK (surface IS NULL OR surface IN ('OCCLUSAL', 'MESIAL', 'DISTAL', 'VESTIBULAR', 'LINGUAL')),
    CONSTRAINT odontogram_entry_surface_condition_check
        CHECK (surface IS NULL OR condition IN ('CARIES', 'FILLED')),
    CONSTRAINT odontogram_entry_notes_check CHECK (notes IS NULL OR char_length(notes) <= 500),
    CONSTRAINT odontogram_entry_record_id_unique UNIQUE (clinical_record_id, id),
    -- Convencion de correccion, identica a anamnesis_entry (el sufijo corrects_once_unique lo traduce a 409 la app).
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
