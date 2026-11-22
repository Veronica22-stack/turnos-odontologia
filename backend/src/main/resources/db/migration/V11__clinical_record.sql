-- C-16 clinical-record-core.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Historia clinica append-only: contenedor por paciente y anamnesis versionada con correcciones que referencian
-- a la entrada original. La inmutabilidad se garantiza revocando UPDATE/DELETE/TRUNCATE al rol de la aplicacion.

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
    CONSTRAINT anamnesis_entry_version_check CHECK (version >= 1),
    CONSTRAINT anamnesis_entry_content_check CHECK (jsonb_typeof(content) = 'object'
                                                    AND octet_length(content::text) <= 16384),
    CONSTRAINT anamnesis_entry_record_version_unique UNIQUE (clinical_record_id, version),
    CONSTRAINT anamnesis_entry_record_id_unique UNIQUE (clinical_record_id, id),
    -- Convencion de correccion, identica para las demas tablas de entradas clinicas (C-17/C-18).
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
CREATE INDEX anamnesis_entry_author_idx ON anamnesis_entry (author_id);

REVOKE UPDATE, DELETE, TRUNCATE ON clinical_record FROM ${app_role};
REVOKE UPDATE, DELETE, TRUNCATE ON anamnesis_entry FROM ${app_role};
