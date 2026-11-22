-- C-10 patients-management.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Crea la tabla patient y restringe privilegios para forzar baja logica (sin DELETE para ${app_role}).

CREATE TABLE patient (
    id          uuid         PRIMARY KEY,
    practice_id uuid         NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    first_name  varchar(100) NOT NULL,
    last_name   varchar(100) NOT NULL,
    dni         varchar(20)  NOT NULL,
    phone       varchar(50)  NOT NULL,
    email       varchar(255),
    birth_date  date,
    address     varchar(255),
    source      varchar(20)  NOT NULL DEFAULT 'MANUAL',
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT patient_source_check CHECK (source IN ('MANUAL', 'PUBLIC_BOOKING')),
    CONSTRAINT patient_practice_dni_unique UNIQUE (practice_id, dni)
);

CREATE INDEX patient_practice_active_idx ON patient (practice_id, active);
CREATE INDEX patient_search_name_idx ON patient (practice_id, lower(last_name), lower(first_name));
CREATE INDEX patient_search_dni_idx ON patient (practice_id, dni);
CREATE INDEX patient_search_phone_idx ON patient (practice_id, phone);

REVOKE DELETE, TRUNCATE ON patient FROM ${app_role};
