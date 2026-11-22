-- C-03 audit-log. Se ejecuta con el rol de migracion; ${app_role} es el rol de la aplicacion.
-- Rastro de accesos append-only: la aplicacion solo INSERT/SELECT (V1 da el DML por defecto; aca se revoca la mutacion).
-- patient_id no tiene FK: la tabla patient se crea recien en C-10.

CREATE TABLE audit_log (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    practice_id   uuid        NOT NULL REFERENCES practice (id) ON DELETE RESTRICT,
    user_id       uuid        NULL REFERENCES app_user (id) ON DELETE RESTRICT,
    action        text        NOT NULL,
    entity_type   text        NULL,
    entity_id     text        NULL,
    patient_id    uuid        NULL,
    ip            inet        NULL,
    user_agent    text        NULL,
    occurred_at   timestamptz NOT NULL DEFAULT now(),
    details       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT audit_log_action_check
        CHECK (action IN ('LOGIN_OK','LOGIN_FAIL','PATIENT_READ','PATIENT_UPDATE',
                          'HC_READ','HC_CREATE','HC_CORRECT','BOOKING_TOKEN_USE')),
    CONSTRAINT audit_log_entity_type_check CHECK (char_length(entity_type) <= 100),
    CONSTRAINT audit_log_entity_id_check   CHECK (char_length(entity_id) <= 200),
    CONSTRAINT audit_log_user_agent_check  CHECK (char_length(user_agent) <= 512),
    CONSTRAINT audit_log_details_object_check CHECK (jsonb_typeof(details) = 'object'),
    CONSTRAINT audit_log_details_no_pii_check
        CHECK (NOT (details ?| ARRAY['dni','document','documento','email','mail',
                                     'phone','telefono','name','nombre','apellido',
                                     'fullname','password','pass','secret','token',
                                     'authorization','header','body','content',
                                     'description','detalle','clinical','diagnostico',
                                     'alergia','medicacion','evolucion','note']))
);

CREATE INDEX audit_log_practice_occurred_idx ON audit_log (practice_id, occurred_at DESC);
CREATE INDEX audit_log_user_occurred_idx     ON audit_log (user_id, occurred_at DESC);
CREATE INDEX audit_log_patient_occurred_idx  ON audit_log (patient_id, occurred_at DESC)
    WHERE patient_id IS NOT NULL;
CREATE INDEX audit_log_action_occurred_idx   ON audit_log (action, occurred_at DESC);

REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM ${app_role};
