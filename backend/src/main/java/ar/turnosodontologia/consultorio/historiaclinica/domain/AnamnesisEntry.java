package ar.turnosodontologia.consultorio.historiaclinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Version de la anamnesis de un paciente; una correccion es una nueva version que referencia a la corregida. */
@Entity
@Immutable
@Table(name = "anamnesis_entry")
public class AnamnesisEntry extends ClinicalEntry {

    @Column(name = "version", nullable = false, updatable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "jsonb")
    private AnamnesisContent content;

    protected AnamnesisEntry() {
        // JPA
    }

    private AnamnesisEntry(
            UUID clinicalRecordId, int version, AnamnesisContent content, EntryProvenance provenance,
            Correction correction) {
        super(clinicalRecordId, provenance, correction);
        if (version < 1) {
            throw new IllegalArgumentException("version debe ser >= 1");
        }
        this.version = version;
        this.content = Objects.requireNonNull(content, "content");
    }

    public static AnamnesisEntry register(
            UUID clinicalRecordId, int version, AnamnesisContent content, EntryProvenance provenance) {
        return new AnamnesisEntry(clinicalRecordId, version, content, provenance, null);
    }

    public static AnamnesisEntry correct(
            UUID clinicalRecordId, int version, AnamnesisContent content, Correction correction,
            EntryProvenance provenance) {
        return new AnamnesisEntry(
                clinicalRecordId, version, content, provenance, Objects.requireNonNull(correction, "correction"));
    }

    public int version() {
        return version;
    }

    public AnamnesisContent content() {
        return content;
    }
}
