package ar.turnosodontologia.consultorio.notificaciones.domain;

/**
 * Estado de la fila de la cola. {@code DISCARDED} = no correspondia enviar; {@code FAILED} = correspondia y no se
 * pudo (design D6).
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    DISCARDED
}
