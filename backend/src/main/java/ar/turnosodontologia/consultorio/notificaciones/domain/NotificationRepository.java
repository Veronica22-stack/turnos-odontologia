package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de la cola de notificaciones (design D4, D5). Los alta/reset son upserts atomicos por
 * {@code (appointment_id, type)}; deben llamarse dentro de una transaccion.
 */
public interface NotificationRepository {

    /** Crea la notificacion {@code PENDING} si no existe para el turno y tipo; si existe no cambia nada. */
    void enqueueIfAbsent(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor, Instant now);

    /** Crea la notificacion o la reinicia ({@code PENDING}, sin intentos ni envio) con el nuevo horario. */
    void upsertPending(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor, Instant now);

    /**
     * Descarta las pendientes del turno que no son de cancelacion (las propias de cancelacion no se tocan para que
     * reprocesar el evento no las anule). Devuelve cuantas descarto.
     */
    int discardPendingForCancellation(UUID appointmentId, NotificationErrorCode code, Instant now);

    /** Descarta la notificacion {@code PENDING} de ese tipo, si existe. Devuelve cuantas descarto (0 o 1). */
    int discardPendingOfType(UUID appointmentId, NotificationType type, NotificationErrorCode code, Instant now);

    /**
     * Toma, bloqueandola ({@code FOR UPDATE SKIP LOCKED}), la {@code PENDING} vencida mas antigua que ningun otro
     * despachador este procesando.
     */
    Optional<Notification> claimNextDue(Instant now);

    Optional<Notification> findByAppointmentIdAndType(UUID appointmentId, NotificationType type);

    <S extends Notification> S save(S notification);
}
