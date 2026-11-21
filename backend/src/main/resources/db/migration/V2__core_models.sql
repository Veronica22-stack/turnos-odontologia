-- C-02 core-models-shared: consultorio de la instalacion (practice) y cuentas de usuario (app_user).
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Las claves primarias son UUID v7 generados por la aplicacion (sin DEFAULT en la base).
-- Los timestamps son timestamptz (UTC); la zona de presentacion es practice.timezone.

CREATE TABLE practice (
    id             uuid        PRIMARY KEY,
    name           text        NOT NULL,
    timezone       text        NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    contact_email  text        NULL,
    contact_phone  text        NULL,
    address        text        NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT practice_name_check          CHECK (btrim(name) <> '' AND char_length(name) <= 200),
    CONSTRAINT practice_timezone_check      CHECK (btrim(timezone) <> ''),
    CONSTRAINT practice_contact_email_check CHECK (char_length(contact_email) <= 254),
    CONSTRAINT practice_contact_phone_check CHECK (char_length(contact_phone) <= 50),
    CONSTRAINT practice_address_check       CHECK (char_length(address) <= 300)
);

-- v1 single-tenant: a lo sumo una fila. Se elimina con DROP INDEX al pasar a multi-tenant (DD-02).
CREATE UNIQUE INDEX practice_single_row ON practice ((true));

-- Nombre provisional: el odontologo lo reemplaza desde la configuracion (C-05). Sin datos de contacto inventados.
INSERT INTO practice (id, name) VALUES ('01900000-0000-7000-8000-000000000001', 'Consultorio');

CREATE TABLE app_user (
    id                    uuid        PRIMARY KEY,
    practice_id           uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    email                 text        NOT NULL,
    password_hash         text        NOT NULL,
    role                  text        NOT NULL,
    full_name             text        NULL,
    license_number        text        NULL,
    failed_attempts       integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz NULL,
    must_change_password  boolean     NOT NULL DEFAULT false,
    created_at            timestamptz NOT NULL DEFAULT now(),
    last_login_at         timestamptz NULL,
    CONSTRAINT app_user_email_check           CHECK (email = lower(btrim(email)) AND char_length(email) BETWEEN 3 AND 254),
    CONSTRAINT app_user_password_hash_check   CHECK (password_hash <> ''),
    -- text + CHECK (no ENUM): sumar un rol es reemplazar este CHECK en una migracion nueva.
    CONSTRAINT app_user_role_check            CHECK (role IN ('DENTIST')),
    CONSTRAINT app_user_full_name_check       CHECK (char_length(full_name) <= 200),
    CONSTRAINT app_user_license_number_check  CHECK (char_length(license_number) <= 50),
    CONSTRAINT app_user_failed_attempts_check CHECK (failed_attempts >= 0)
);

-- Unicidad global del email (el login identifica al usuario solo por email); ver design D6.
CREATE UNIQUE INDEX app_user_email_key ON app_user (lower(email));
CREATE INDEX app_user_practice_id_idx ON app_user (practice_id);

-- Mas estricto que el DML por defecto de V1: el consultorio se edita pero no se crea ni se borra desde la
-- aplicacion, y los usuarios no se borran.
REVOKE INSERT, DELETE ON practice FROM ${app_role};
REVOKE DELETE ON app_user FROM ${app_role};
