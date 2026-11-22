package ar.turnosodontologia.consultorio.auditoria.api;

/** Catalogo de acciones auditables; se alinea con el CHECK de la migracion V3 (design D1/D2). */
public enum AuditAction {
    LOGIN_OK,
    LOGIN_FAIL,
    PATIENT_READ,
    PATIENT_UPDATE,
    HC_READ,
    HC_CREATE,
    HC_CORRECT,
    BOOKING_TOKEN_USE
}
