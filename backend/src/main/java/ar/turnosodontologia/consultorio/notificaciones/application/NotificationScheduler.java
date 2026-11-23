package ar.turnosodontologia.consultorio.notificaciones.application;

import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.ReminderSchedule;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encolado de notificaciones por evento de turno (design D3, D4). Cada metodo corre en una transaccion nueva: el
 * listener lo invoca despues del commit del turno y un fallo aca nunca debe revertir ese turno. Todos los altas son
 * upserts por {@code (turno, tipo)}, asi que reprocesar un evento no duplica ni reinicia lo ya enviado.
 */
@Service
public class NotificationScheduler {

    private final NotificationRepository notifications;
    private final PracticeRepository practices;
    private final Clock clock;

    public NotificationScheduler(NotificationRepository notifications, PracticeRepository practices, Clock clock) {
        this.notifications = notifications;
        this.practices = practices;
        this.clock = clock;
    }

    /** Turno confirmado: confirmacion inmediata y recordatorio segun la anticipacion vigente del consultorio. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onConfirmed(UUID practiceId, UUID appointmentId, Instant startsAt) {
        Instant now = clock.instant();
        notifications.enqueueIfAbsent(practiceId, appointmentId, NotificationType.CONFIRMATION, now, now);
        reminderAt(practiceId, startsAt).ifPresent(at ->
                notifications.enqueueIfAbsent(practiceId, appointmentId, NotificationType.REMINDER, at, now));
    }

    /** Turno reprogramado: aviso inmediato y recordatorio recalculado (o descartado si ya esta en la ventana). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRescheduled(UUID practiceId, UUID appointmentId, Instant newStartsAt) {
        Instant now = clock.instant();
        notifications.upsertPending(practiceId, appointmentId, NotificationType.RESCHEDULE, now, now);
        Optional<Instant> reminderAt = reminderAt(practiceId, newStartsAt);
        if (reminderAt.isPresent()) {
            notifications.upsertPending(practiceId, appointmentId, NotificationType.REMINDER, reminderAt.get(), now);
        } else {
            notifications.discardPendingOfType(appointmentId, NotificationType.REMINDER,
                    NotificationErrorCode.REMINDER_WINDOW_PASSED, now);
        }
    }

    /**
     * Turno cancelado: descarta lo pendiente (para que nunca salga un recordatorio de un turno cancelado) y encola la
     * cancelacion; si cancelo el paciente, ademas el aviso al odontologo.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCancelled(UUID practiceId, UUID appointmentId, boolean cancelledByPatient) {
        Instant now = clock.instant();
        notifications.discardPendingForCancellation(appointmentId, NotificationErrorCode.APPOINTMENT_CANCELLED, now);
        notifications.enqueueIfAbsent(practiceId, appointmentId, NotificationType.CANCELLATION, now, now);
        if (cancelledByPatient) {
            notifications.enqueueIfAbsent(practiceId, appointmentId, NotificationType.DENTIST_CANCELLATION, now, now);
        }
    }

    private Optional<Instant> reminderAt(UUID practiceId, Instant startsAt) {
        Practice practice = practices.findById(practiceId).orElseThrow(
                () -> new IllegalStateException("Consultorio inexistente para la notificacion"));
        return ReminderSchedule.reminderAt(startsAt, practice.reminderHoursBefore(), clock);
    }
}
