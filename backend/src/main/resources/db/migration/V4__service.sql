-- C-07 services-catalog.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Crea la tabla service y restringe los privilegios para forzar baja logica (sin DELETE/TRUNCATE para ${app_role}).

CREATE TABLE service (
    id             uuid         PRIMARY KEY,
    practice_id    uuid         NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    name           varchar(120) NOT NULL,
    description    varchar(500),
    duration_min   integer      NOT NULL,
    active         boolean      NOT NULL DEFAULT true,
    public_visible boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT service_duration_min_check CHECK (duration_min > 0)
);

CREATE INDEX service_practice_name_idx ON service (practice_id, name);
CREATE INDEX service_public_idx ON service (practice_id, active, public_visible);

REVOKE DELETE, TRUNCATE ON service FROM ${app_role};
