package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false)
    private UUID practiceId;

    @Column(name = "weekday", nullable = false, columnDefinition = "smallint")
    private short weekday;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHours() {
        // JPA
    }

    private WorkingHours(UUID id, UUID practiceId, int weekday, LocalTime startTime, LocalTime endTime) {
        validate(practiceId, weekday, startTime, endTime);
        this.id = id;
        this.practiceId = practiceId;
        this.weekday = (short) weekday;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public static WorkingHours create(UUID practiceId, int weekday, LocalTime startTime, LocalTime endTime) {
        return new WorkingHours(null, practiceId, weekday, startTime, endTime);
    }

    public static WorkingHours existing(UUID id, UUID practiceId, int weekday, LocalTime startTime, LocalTime endTime) {
        return new WorkingHours(id, practiceId, weekday, startTime, endTime);
    }

    private static void validate(UUID practiceId, int weekday, LocalTime startTime, LocalTime endTime) {
        if (practiceId == null) {
            throw new ValidationException("practice_id", "practice_id no puede ser nulo");
        }
        if (weekday < 1 || weekday > 7) {
            throw new ValidationException("weekday", "weekday debe estar entre 1 y 7");
        }
        if (startTime == null) {
            throw new ValidationException("start_time", "start_time no puede ser nulo");
        }
        if (endTime == null) {
            throw new ValidationException("end_time", "end_time no puede ser nulo");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ValidationException("end_time", "end_time debe ser posterior a start_time");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public int weekday() {
        return weekday;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public LocalTime endTime() {
        return endTime;
    }
}
