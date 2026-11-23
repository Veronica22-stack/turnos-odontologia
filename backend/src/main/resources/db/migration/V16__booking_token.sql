-- V16: tokens de gestion de la reserva publica (C-14).
-- V15 es la ultima version aplicada; V10 quedo libre por merges paralelos y con outOfOrder=false
-- no se puede insertar una version menor. No se tocan las migraciones V1..V15.
-- Desvio respecto de KB 04: se agregan privacy_notice_version y privacy_accepted_at (PA-10)
-- y se permite mas de un token por turno (C-13 D11 emite uno nuevo por correo).
-- El token en claro NUNCA se persiste: solo su hash SHA-256.

CREATE TABLE booking_token (
    id uuid PRIMARY KEY,
    appointment_id uuid NOT NULL REFERENCES appointment (id) ON DELETE RESTRICT,
    token_hash text NOT NULL CONSTRAINT booking_token_hash_unique UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    privacy_notice_version text NOT NULL,
    privacy_accepted_at timestamptz NOT NULL,
    CONSTRAINT booking_token_hash_check CHECK (char_length(token_hash) = 64)
);

CREATE INDEX booking_token_appointment_idx ON booking_token (appointment_id);

REVOKE DELETE, TRUNCATE ON booking_token FROM ${app_role};
