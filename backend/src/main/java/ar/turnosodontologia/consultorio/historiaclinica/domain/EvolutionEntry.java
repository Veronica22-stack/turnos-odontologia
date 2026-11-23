package ar.turnosodontologia.consultorio.historiaclinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Evolucion clinica: asiento libre de una atencion, con turno opcional. Append-only: una correccion es otra entrada
 * que referencia a la original y conserva su turno.
 */
@Entity
@Immutable
@Table(name = "evolution_entry")
public class EvolutionEntry extends ClinicalEntry {

    @Column(name = "appointment_id", updatable = false)
    private UUID appointmentId;

    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    protected EvolutionEntry() {
        // JPA
    }

    private EvolutionEntry(
            UUID clinicalRecordId, EvolutionDescription description, UUID appointmentId, EntryProvenance provenance,
            Correction correction) {
        super(clinicalRecordId, provenance, correction);
        this.description = Objects.requireNonNull(description, "description").value();
        this.appointmentId = appointmentId;
    }

    public static EvolutionEntry register(
            UUID clinicalRecordId, EvolutionDescription description, UUID appointmentId, EntryProvenance provenance) {
        return new EvolutionEntry(clinicalRecordId, description, appointmentId, provenance, null);
    }

    /** El turno se copia de la original: no se corrige, solo la descripcion. */
    public static EvolutionEntry correct(
            UUID clinicalRecordId, EvolutionEntry original, EvolutionDescription description, Correction correction,
            EntryProvenance provenance) {
        Objects.requireNonNull(original, "original");
        return new EvolutionEntry(
                clinicalRecordId, description, original.appointmentId(), provenance,
                Objects.requireNonNull(correction, "correction"));
    }

    public UUID appointmentId() {
        return appointmentId;
    }

    public String description() {
        return description;
    }
}
