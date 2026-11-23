package ar.turnosodontologia.consultorio.notificaciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;

/**
 * Fila de la cola de notificaciones (design D1). No guarda destinatario, asunto, cuerpo ni link: todo se resuelve al
 * enviar, asi la tabla no acumula datos personales ni tokens. Solo una notificacion {@code PENDING} puede cambiar de
 * estado por un envio; los estados terminales no se reabren desde aqui (el reencolado es un upsert del repositorio).
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;

    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notification() {
        // JPA
    }

    private Notification(UUID practiceId, UUID appointmentId, NotificationType type, Instant scheduledFor,
            Instant now) {
        this.id = UuidVersion7Strategy.INSTANCE.generateUuid(null);
        this.practiceId = practiceId;
        this.appointmentId = appointmentId;
        this.type = type;
        this.status = NotificationStatus.PENDING;
        this.scheduledFor = scheduledFor;
        this.attempts = 0;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Notificacion nueva, {@code PENDING} y sin intentos. */
    public static Notification pending(UUID practiceId, UUID appointmentId, NotificationType type,
            Instant scheduledFor, Instant now) {
        return new Notification(practiceId, appointmentId, type, scheduledFor, now);
    }

    public void markSent(Instant now) {
        requirePending();
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    /** Fallo transitorio: cuenta el intento, guarda el codigo y reprograma. */
    public void markRetry(NotificationErrorCode code, Instant nextScheduledFor, Instant now) {
        requirePending();
        this.attempts++;
        this.lastError = code.name();
        this.scheduledFor = nextScheduledFor;
        this.updatedAt = now;
    }

    /** Fallo definitivo: cuenta el intento, guarda el codigo y no se vuelve a intentar. */
    public void markFailed(NotificationErrorCode code, Instant now) {
        requirePending();
        this.attempts++;
        this.status = NotificationStatus.FAILED;
        this.lastError = code.name();
        this.updatedAt = now;
    }

    /** No correspondia enviar (turno cancelado, vencido, sin destinatario...): no cuenta como intento. */
    public void markDiscarded(NotificationErrorCode code, Instant now) {
        requirePending();
        this.status = NotificationStatus.DISCARDED;
        this.lastError = code.name();
        this.updatedAt = now;
    }

    private void requirePending() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException("La notificacion no esta pendiente: " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public UUID appointmentId() {
        return appointmentId;
    }

    public NotificationType type() {
        return type;
    }

    public NotificationStatus status() {
        return status;
    }

    public Instant scheduledFor() {
        return scheduledFor;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public int attempts() {
        return attempts;
    }

    public String lastError() {
        return lastError;
    }

    /** Solo identificadores, tipo y estado: nunca datos de contenido. */
    @Override
    public String toString() {
        return "Notification[id=" + id + ", appointmentId=" + appointmentId + ", type=" + type + ", status=" + status
                + ", attempts=" + attempts + "]";
    }
}
