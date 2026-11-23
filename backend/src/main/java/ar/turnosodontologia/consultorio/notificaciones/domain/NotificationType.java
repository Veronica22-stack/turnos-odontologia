package ar.turnosodontologia.consultorio.notificaciones.domain;

/** Tipos de notificacion de un turno (design D1); a lo sumo una por turno y tipo. */
public enum NotificationType {
    CONFIRMATION,
    REMINDER,
    RESCHEDULE,
    CANCELLATION,
    DENTIST_CANCELLATION
}
