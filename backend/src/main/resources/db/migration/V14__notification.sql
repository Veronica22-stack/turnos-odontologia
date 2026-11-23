-- C-13 email-notifications-outbox. Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion (DB_USER).
-- Numero V14: es la siguiente version libre (V13 es appointment, C-11; V6/V9/V10 quedaron como huecos por merges
-- paralelos). Flyway corre con outOfOrder=false, asi que un numero menor o repetido rompe el arranque. No se tocan V1-V13.
-- Cola (outbox) de notificaciones por email de un turno (RN-NO-01..06). Una sola fila por (turno, tipo): reprogramar
-- reutiliza la fila de RESCHEDULE y del REMINDER (se reinicia) y el reproceso de un evento no duplica nada.
-- Desvios respecto de KB 04: se agrega practice_id (DD-02), el tipo DENTIST_CANCELLATION (aviso al odontologo ante una
-- cancelacion del paciente, PA-14), el estado DISCARDED (no correspondia enviar, a diferencia de FAILED) y
-- created_at/updated_at. NO se guarda destinatario, asunto, cuerpo ni link: se resuelven al enviar, asi la tabla no
-- acumula datos personales ni tokens. last_error guarda solo un codigo de un catalogo cerrado (max 64 caracteres).

CREATE TABLE notification (
    id             uuid        PRIMARY KEY,
    practice_id    uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    appointment_id uuid        NOT NULL REFERENCES appointment (id) ON DELETE RESTRICT,
    type           text        NOT NULL,
    channel        text        NOT NULL DEFAULT 'EMAIL',
    status         text        NOT NULL DEFAULT 'PENDING',
    scheduled_for  timestamptz NOT NULL,
    sent_at        timestamptz,
    attempts       integer     NOT NULL DEFAULT 0,
    last_error     text,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT notification_type_check
        CHECK (type IN ('CONFIRMATION', 'REMINDER', 'RESCHEDULE', 'CANCELLATION', 'DENTIST_CANCELLATION')),
    CONSTRAINT notification_channel_check CHECK (channel = 'EMAIL'),
    CONSTRAINT notification_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DISCARDED')),
    CONSTRAINT notification_attempts_check CHECK (attempts >= 0),
    CONSTRAINT notification_last_error_check CHECK (char_length(last_error) <= 64),
    CONSTRAINT notification_sent_at_check CHECK ((status = 'SENT') = (sent_at IS NOT NULL)),
    CONSTRAINT notification_appointment_type_key UNIQUE (appointment_id, type)
);

-- Despacho: solo las pendientes, ordenadas por vencimiento. La busqueda por turno la cubre el indice del UNIQUE
-- (appointment_id es su columna lider), por eso no hay otro indice sobre appointment_id.
CREATE INDEX notification_pending_due_idx ON notification (scheduled_for) WHERE status = 'PENDING';

-- Una notificacion solo cambia de estado: sin borrado fisico. SELECT/INSERT/UPDATE (y FOR UPDATE) se conservan.
REVOKE DELETE, TRUNCATE ON notification FROM ${app_role};
