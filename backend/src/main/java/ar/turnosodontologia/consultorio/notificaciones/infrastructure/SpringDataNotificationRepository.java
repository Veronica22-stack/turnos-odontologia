package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.domain.Notification;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Implementacion Spring Data del puerto de la cola. Los alta/reset son {@code INSERT ... ON CONFLICT} nativos:
 * atomicos frente a duplicados concurrentes y sin excepcion que capturar. La toma de la proxima fila usa
 * {@code FOR UPDATE SKIP LOCKED} para que varios despachadores nunca procesen la misma notificacion (design D4, D5).
 */
interface SpringDataNotificationRepository extends JpaRepository<Notification, UUID>, NotificationRepository {

    @Override
    default void enqueueIfAbsent(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor,
            Instant now) {
        insertIfAbsent(newId(), practiceId, appointmentId, type.name(), scheduledFor, now);
    }

    @Override
    default void upsertPending(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor,
            Instant now) {
        upsertAsPending(newId(), practiceId, appointmentId, type.name(), scheduledFor, now);
    }

    @Override
    default int discardPendingForCancellation(UUID appointmentId, NotificationErrorCode code, Instant now) {
        return discardNonCancellation(appointmentId, code.name(), now);
    }

    @Override
    default int discardPendingOfType(UUID appointmentId, NotificationType type, NotificationErrorCode code,
            Instant now) {
        return discardOfType(appointmentId, type.name(), code.name(), now);
    }

    @Override
    default Optional<Notification> claimNextDue(Instant now) {
        return lockNextDue(now);
    }

    @Override
    Optional<Notification> findByAppointmentIdAndType(UUID appointmentId, NotificationType type);

    private static UUID newId() {
        return UuidVersion7Strategy.INSTANCE.generateUuid(null);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification (id, practice_id, appointment_id, type, status, scheduled_for, attempts,
                                      created_at, updated_at)
            VALUES (:id, :practiceId, :appointmentId, :type, 'PENDING', :scheduledFor, 0, :now, :now)
            ON CONFLICT (appointment_id, type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("practiceId") UUID practiceId,
            @Param("appointmentId") UUID appointmentId, @Param("type") String type,
            @Param("scheduledFor") Instant scheduledFor, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO notification (id, practice_id, appointment_id, type, status, scheduled_for, attempts,
                                      created_at, updated_at)
            VALUES (:id, :practiceId, :appointmentId, :type, 'PENDING', :scheduledFor, 0, :now, :now)
            ON CONFLICT (appointment_id, type) DO UPDATE
               SET status = 'PENDING', scheduled_for = EXCLUDED.scheduled_for, attempts = 0, sent_at = NULL,
                   last_error = NULL, updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    int upsertAsPending(@Param("id") UUID id, @Param("practiceId") UUID practiceId,
            @Param("appointmentId") UUID appointmentId, @Param("type") String type,
            @Param("scheduledFor") Instant scheduledFor, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification
               SET status = 'DISCARDED', last_error = :code, updated_at = :now
             WHERE appointment_id = :appointmentId AND status = 'PENDING'
               AND type NOT IN ('CANCELLATION', 'DENTIST_CANCELLATION')
            """, nativeQuery = true)
    int discardNonCancellation(@Param("appointmentId") UUID appointmentId, @Param("code") String code,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE notification
               SET status = 'DISCARDED', last_error = :code, updated_at = :now
             WHERE appointment_id = :appointmentId AND type = :type AND status = 'PENDING'
            """, nativeQuery = true)
    int discardOfType(@Param("appointmentId") UUID appointmentId, @Param("type") String type,
            @Param("code") String code, @Param("now") Instant now);

    @Query(value = """
            SELECT * FROM notification
             WHERE status = 'PENDING' AND scheduled_for <= :now
             ORDER BY scheduled_for, id
             LIMIT 1
               FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notification> lockNextDue(@Param("now") Instant now);
}
