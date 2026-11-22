package ar.turnosodontologia.consultorio.historiaclinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Estado de una pieza (o de una cara) en un instante. Append-only: una correccion es otra entrada en la misma
 * posicion que referencia a la original.
 */
@Entity
@Immutable
@Table(name = "odontogram_entry")
public class OdontogramEntry extends ClinicalEntry {

    public static final int NOTES_MAX_LENGTH = 500;

    @Column(name = "tooth_code", nullable = false, updatable = false)
    private String toothCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface", updatable = false)
    private ToothSurface surface;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition", nullable = false, updatable = false)
    private ToothCondition condition;

    @Column(name = "notes", updatable = false)
    private String notes;

    protected OdontogramEntry() {
        // JPA
    }

    private OdontogramEntry(
            UUID clinicalRecordId, ToothPosition position, ToothCondition condition, String notes,
            EntryProvenance provenance, Correction correction) {
        super(clinicalRecordId, provenance, correction);
        Objects.requireNonNull(position, "position");
        this.condition = Objects.requireNonNull(condition, "condition");
        if (position.hasSurface() && !condition.allowsSurface()) {
            throw new IllegalArgumentException("el estado no admite cara");
        }
        if (notes != null && notes.length() > NOTES_MAX_LENGTH) {
            throw new IllegalArgumentException("notes excede " + NOTES_MAX_LENGTH + " caracteres");
        }
        this.toothCode = position.tooth().value();
        this.surface = position.surface();
        this.notes = notes;
    }

    public static OdontogramEntry register(
            UUID clinicalRecordId, ToothPosition position, ToothCondition condition, String notes,
            EntryProvenance provenance) {
        return new OdontogramEntry(clinicalRecordId, position, condition, notes, provenance, null);
    }

    /** La posicion (pieza y cara) se copia de la original: no se corrige, solo el estado y las notas. */
    public static OdontogramEntry correct(
            UUID clinicalRecordId, OdontogramEntry original, ToothCondition condition, String notes,
            Correction correction, EntryProvenance provenance) {
        Objects.requireNonNull(original, "original");
        return new OdontogramEntry(
                clinicalRecordId, original.position(), condition, notes, provenance,
                Objects.requireNonNull(correction, "correction"));
    }

    public ToothPosition position() {
        return new ToothPosition(ToothCode.of(toothCode), surface);
    }

    public ToothCondition condition() {
        return condition;
    }

    public String notes() {
        return notes;
    }
}
