package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Bloqueo de agenda del consultorio; el rango es semiabierto {@code [startsAt, endsAt)} en UTC. */
@Entity
@Table(name = "schedule_block")
public class ScheduleBlock {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String PRACTICE_ID_REQUIRED = "practice_id no puede ser nulo";
    private static final String REASON_REQUIRED = "reason no puede ser vacío";
    private static final String REASON_TOO_LONG = "reason no puede superar " + MAX_REASON_LENGTH + " caracteres";

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false)
    private UUID practiceId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "reason", nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScheduleBlock() {
        // JPA
    }

    private ScheduleBlock(UUID id, UUID practiceId, Instant startsAt, Instant endsAt, String reason,
            Instant createdAt, Instant updatedAt) {
        validate(practiceId, startsAt, endsAt, reason);
        this.id = id;
        this.practiceId = practiceId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ScheduleBlock create(UUID practiceId, Instant startsAt, Instant endsAt, String reason, Clock clock) {
        Instant now = clock.instant();
        return new ScheduleBlock(null, practiceId, startsAt, endsAt, reason, now, now);
    }

    public static ScheduleBlock existing(UUID id, UUID practiceId, Instant startsAt, Instant endsAt, String reason,
            Instant createdAt, Instant updatedAt) {
        return new ScheduleBlock(id, practiceId, startsAt, endsAt, reason, createdAt, updatedAt);
    }

    public void update(Instant startsAt, Instant endsAt, String reason, Clock clock) {
        validate(practiceId, startsAt, endsAt, reason);
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.updatedAt = clock.instant();
    }

    private static void validate(UUID practiceId, Instant startsAt, Instant endsAt, String reason) {
        if (practiceId == null) {
            throw new ValidationException("practice_id", PRACTICE_ID_REQUIRED);
        }
        new TimeInterval(startsAt, endsAt);
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason", REASON_REQUIRED);
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new ValidationException("reason", REASON_TOO_LONG);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public Instant startsAt() {
        return startsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public String reason() {
        return reason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
