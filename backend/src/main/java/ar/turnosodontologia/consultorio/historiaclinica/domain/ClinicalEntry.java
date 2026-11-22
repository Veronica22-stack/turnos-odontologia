package ar.turnosodontologia.consultorio.historiaclinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * Base de toda entrada clinica: append-only. Sin setters y con columnas {@code updatable = false}; una correccion es
 * otra entrada que referencia a la original.
 */
@MappedSuperclass
@Immutable
public abstract class ClinicalEntry {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "clinical_record_id", nullable = false, updatable = false)
    private UUID clinicalRecordId;

    @Column(name = "corrects_entry_id", updatable = false)
    private UUID correctsEntryId;

    @Column(name = "correction_reason", updatable = false)
    private String correctionReason;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ClinicalEntry() {
        // JPA
    }

    protected ClinicalEntry(UUID clinicalRecordId, EntryProvenance provenance, Correction correction) {
        this.clinicalRecordId = Objects.requireNonNull(clinicalRecordId, "clinicalRecordId");
        Objects.requireNonNull(provenance, "provenance");
        this.authorId = provenance.authorId();
        this.createdAt = provenance.createdAt();
        if (correction != null) {
            this.correctsEntryId = correction.originalEntryId();
            this.correctionReason = correction.reason().value();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID clinicalRecordId() {
        return clinicalRecordId;
    }

    public UUID correctsEntryId() {
        return correctsEntryId;
    }

    public String correctionReason() {
        return correctionReason;
    }

    public UUID authorId() {
        return authorId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isCorrection() {
        return correctsEntryId != null;
    }

    /** Solo identificadores: el contenido y el motivo son datos clinicos (regla dura 6). */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[id=" + id + ", clinicalRecordId=" + clinicalRecordId + "]";
    }
}
