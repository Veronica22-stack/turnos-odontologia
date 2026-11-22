-- C-08 working-hours-booking-settings.
-- Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Extiende practice con parametros de reserva y crea la tabla working_hours.

ALTER TABLE practice
    ADD COLUMN slot_granularity_min  integer NOT NULL DEFAULT 15,
    ADD COLUMN min_notice_min        integer NOT NULL DEFAULT 120,
    ADD COLUMN max_advance_days      integer NOT NULL DEFAULT 30,
    ADD COLUMN reminder_hours_before integer NOT NULL DEFAULT 24,
    ADD CONSTRAINT practice_slot_granularity_min_check CHECK (slot_granularity_min IN (10, 15, 20, 30, 60)),
    ADD CONSTRAINT practice_min_notice_min_check       CHECK (min_notice_min >= 0),
    ADD CONSTRAINT practice_max_advance_days_check     CHECK (max_advance_days BETWEEN 1 AND 365),
    ADD CONSTRAINT practice_reminder_hours_before_check CHECK (reminder_hours_before BETWEEN 1 AND 168);

CREATE TABLE working_hours (
    id          uuid        PRIMARY KEY,
    practice_id uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    weekday     smallint    NOT NULL,
    start_time  time        NOT NULL,
    end_time    time        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT working_hours_weekday_check    CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT working_hours_time_order_check CHECK (end_time > start_time)
);

CREATE INDEX working_hours_practice_weekday_idx ON working_hours (practice_id, weekday, start_time);

REVOKE TRUNCATE ON working_hours FROM ${app_role};
