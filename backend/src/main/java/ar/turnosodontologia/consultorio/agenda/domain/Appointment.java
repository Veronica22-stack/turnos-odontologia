package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Turno del consultorio (design D2, D6, D7, D14, D15). El rango es semiabierto {@code [startsAt, endsAt)} en UTC.
 * La columna generada {@code period} de la tabla no se mapea: la escribe solo la base.
 */
@Entity
@Table(name = "appointment")
public class Appointment {

    public static final int MAX_NOTES_LENGTH = 1000;

    private static final String IN_PAST = "APPOINTMENT_IN_PAST";
    private static final String NOT_STARTED = "APPOINTMENT_NOT_STARTED";
    private static final String ALREADY_STARTED = "APPOINTMENT_ALREADY_STARTED";
    private static final String INVALID_TRANSITION = "INVALID_STATUS_TRANSITION";

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "duration_min_snapshot", nullable = false, updatable = false)
    private int durationMinSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, updatable = false)
    private AppointmentOrigin origin;

    @Column(name = "overbooking", nullable = false)
    private boolean overbooking;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by")
    private CancelledBy cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "notes")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Appointment() {
        // JPA
    }

    private Appointment(UUID practiceId, UUID patientId, UUID serviceId, Instant startsAt, int durationMinSnapshot,
            AppointmentOrigin origin, boolean overbooking, String notes, Instant now) {
        this.practiceId = practiceId;
        this.patientId = patientId;
        this.serviceId = serviceId;
        this.startsAt = startsAt;
        this.endsAt = startsAt.plus(Duration.ofMinutes(durationMinSnapshot));
        this.durationMinSnapshot = durationMinSnapshot;
        this.status = AppointmentStatus.CONFIRMED;
        this.origin = origin;
        this.overbooking = overbooking;
        this.notes = notes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Turno nuevo {@code CONFIRMED}; {@code startsAt} debe ser estrictamente posterior al instante del reloj. */
    public static Appointment create(UUID practiceId, UUID patientId, UUID serviceId, Instant startsAt,
            int durationMinSnapshot, AppointmentOrigin origin, boolean overbooking, String notes, Clock clock) {
        if (practiceId == null || patientId == null || serviceId == null || startsAt == null || origin == null) {
            throw new ValidationException("appointment", "REQUIRED");
        }
        if (durationMinSnapshot <= 0) {
            throw new ValidationException("duration_min_snapshot", "duration_min_snapshot debe ser mayor que 0");
        }
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new ValidationException("notes", "notes no puede superar " + MAX_NOTES_LENGTH + " caracteres");
        }
        Instant now = clock.instant();
        requireFuture(startsAt, now);
        return new Appointment(practiceId, patientId, serviceId, startsAt, durationMinSnapshot, origin, overbooking,
                notes, now);
    }

    /** {@code CONFIRMED -> CANCELLED} en cualquier momento. */
    public void cancel(CancelledBy by, Clock clock) {
        requireConfirmed();
        Instant now = clock.instant();
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledBy = by;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    /** {@code CONFIRMED -> COMPLETED}, solo si el turno ya empezo ({@code startsAt <= now}). */
    public void complete(Clock clock) {
        finish(AppointmentStatus.COMPLETED, clock);
    }

    /** {@code CONFIRMED -> NO_SHOW}, solo si el turno ya empezo ({@code startsAt <= now}). */
    public void markNoShow(Clock clock) {
        finish(AppointmentStatus.NO_SHOW, clock);
    }

    /**
     * Mueve el turno in-place (design D14): conserva id, paciente, prestacion y duracion snapshot, sin releer la
     * prestacion (D15).
     */
    public void reschedule(Instant newStartsAt, boolean newOverbooking, Clock clock) {
        validateReschedulable(newStartsAt, clock);
        Instant now = clock.instant();
        this.startsAt = newStartsAt;
        this.endsAt = newStartsAt.plus(Duration.ofMinutes(durationMinSnapshot));
        this.overbooking = newOverbooking;
        this.updatedAt = now;
    }

    /**
     * Valida sin modificar nada que el turno se pueda mover a {@code newStartsAt}: debe estar {@code CONFIRMED}, no
     * haber empezado y el nuevo inicio ser futuro. Permite validar antes de tomar el lock (design D18).
     */
    public void validateReschedulable(Instant newStartsAt, Clock clock) {
        requireConfirmed();
        Instant now = clock.instant();
        if (!startsAt.isAfter(now)) {
            throw new BusinessRuleException(ALREADY_STARTED);
        }
        requireFuture(newStartsAt, now);
    }

    /** Fin que tendria el turno si empezara en {@code newStartsAt} (misma duracion snapshot). */
    public Instant endsAtIfStartedAt(Instant newStartsAt) {
        return newStartsAt.plus(Duration.ofMinutes(durationMinSnapshot));
    }

    private void finish(AppointmentStatus result, Clock clock) {
        requireConfirmed();
        Instant now = clock.instant();
        if (startsAt.isAfter(now)) {
            throw new BusinessRuleException(NOT_STARTED);
        }
        this.status = result;
        this.updatedAt = now;
    }

    private void requireConfirmed() {
        if (status != AppointmentStatus.CONFIRMED) {
            throw new ConflictException(INVALID_TRANSITION);
        }
    }

    private static void requireFuture(Instant startsAt, Instant now) {
        if (!startsAt.isAfter(now)) {
            throw new BusinessRuleException(IN_PAST);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public UUID patientId() {
        return patientId;
    }

    public UUID serviceId() {
        return serviceId;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public int durationMinSnapshot() {
        return durationMinSnapshot;
    }

    public AppointmentStatus status() {
        return status;
    }

    public AppointmentOrigin origin() {
        return origin;
    }

    public boolean overbooking() {
        return overbooking;
    }

    public CancelledBy cancelledBy() {
        return cancelledBy;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
