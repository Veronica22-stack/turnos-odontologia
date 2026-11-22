-- C-09 schedule-blocks. Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Numero V8 (no V6): V7__patient.sql ya esta aplicada y Flyway corre con outOfOrder=false. Crear una V6 ahora
-- dejaria una version menor que la ultima aplicada y abortaria el arranque de toda base migrada hasta V7.
-- No se editan ni se renombran V1-V7 (regla dura de migraciones inmutables).
-- Crea la tabla de rangos no reservables de la agenda (US-012, RN-AG-02). El rango period se genera en la base
-- desde starts_at/ends_at con limites semiabiertos '[)': la aplicacion solo escribe columnas timestamptz normales
-- (sin PGobject ni dependencias nuevas). btree_gist ya fue creado en V1.

CREATE TABLE schedule_block (
    id          uuid        PRIMARY KEY,
    practice_id uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    starts_at   timestamptz NOT NULL,
    ends_at     timestamptz NOT NULL,
    reason      text        NOT NULL,
    period      tstzrange GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT schedule_block_reason_check  CHECK (char_length(btrim(reason)) BETWEEN 1 AND 500),
    CONSTRAINT schedule_block_period_check  CHECK (ends_at > starts_at),
    CONSTRAINT schedule_block_no_overlap   EXCLUDE USING gist (practice_id WITH =, period WITH &&)
);

CREATE INDEX schedule_block_practice_starts_idx ON schedule_block (practice_id, starts_at);

-- El bloqueo es configuracion editable: se conservan SELECT/INSERT/UPDATE/DELETE (heredados de V1) y solo se
-- prohibe el borrado masivo. No es dato clinico ni append-only como audit_log/patient.
REVOKE TRUNCATE ON schedule_block FROM ${app_role};
