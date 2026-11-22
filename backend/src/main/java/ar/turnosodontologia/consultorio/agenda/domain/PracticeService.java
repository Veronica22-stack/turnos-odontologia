package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Prestación del catálogo de agenda (design D2/D3): duración real, baja lógica y visibilidad pública
 * independiente. La tabla de la base es {@code service}; el nombre de la entidad evita la colisión con el
 * estereotipo {@code org.springframework.stereotype.Service} en la capa application.
 */
@Entity
@Table(name = "service")
public class PracticeService {

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PracticeService() {
        // JPA
    }

    private PracticeService(
            UUID id,
            UUID practiceId,
            String name,
            String description,
            int durationMin,
            boolean active,
            boolean publicVisible,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.practiceId = practiceId;
        this.name = name;
        this.description = description;
        this.durationMin = durationMin;
        this.active = active;
        this.publicVisible = publicVisible;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static PracticeService create(
            UUID practiceId, String name, String description, int durationMin, boolean publicVisible) {
        if (practiceId == null) {
            throw new ValidationException("practice_id", "practice_id no puede ser nulo");
        }
        Instant now = Instant.now();
        return new PracticeService(
                null,
                practiceId,
                validateName(name),
                normalizeDescription(description),
                validateDurationMin(durationMin),
                true,
                publicVisible,
                now,
                now);
    }

    public static PracticeService existing(
            UUID id,
            UUID practiceId,
            String name,
            String description,
            int durationMin,
            boolean active,
            boolean publicVisible,
            Instant createdAt,
            Instant updatedAt) {
        return new PracticeService(
                id,
                practiceId,
                validateName(name),
                normalizeDescription(description),
                validateDurationMin(durationMin),
                active,
                publicVisible,
                createdAt,
                updatedAt);
    }

    public void update(String name, String description, int durationMin, boolean publicVisible, boolean active) {
        this.name = validateName(name);
        this.description = normalizeDescription(description);
        this.durationMin = validateDurationMin(durationMin);
        this.publicVisible = publicVisible;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int durationMin() {
        return durationMin;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPublicVisible() {
        return publicVisible;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("name", "name debe ser no vacio");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("name", "name debe tener hasta " + MAX_NAME_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("description", "description debe tener hasta " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        return trimmed;
    }

    private static int validateDurationMin(int durationMin) {
        if (durationMin <= 0) {
            throw new ValidationException("duration_min", "duration_min debe ser mayor que 0");
        }
        return durationMin;
    }
}
