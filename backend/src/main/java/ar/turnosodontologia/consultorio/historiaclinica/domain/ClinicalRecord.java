package ar.turnosodontologia.consultorio.historiaclinica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Historia clinica de un paciente: contenedor inmutable que se abre una sola vez, al primer registro clinico. */
@Entity
@Immutable
@Table(name = "clinical_record")
public class ClinicalRecord {

    @Id
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "opened_by", nullable = false, updatable = false)
    private UUID openedBy;

    protected ClinicalRecord() {
        // JPA
    }

    private ClinicalRecord(UUID id, UUID practiceId, UUID patientId, Instant openedAt, UUID openedBy) {
        this.id = Objects.requireNonNull(id, "id");
        this.practiceId = Objects.requireNonNull(practiceId, "practiceId");
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.openedBy = Objects.requireNonNull(openedBy, "openedBy");
    }

    public static ClinicalRecord open(UUID id, UUID practiceId, UUID patientId, Instant openedAt, UUID openedBy) {
        return new ClinicalRecord(id, practiceId, patientId, openedAt, openedBy);
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

    public Instant openedAt() {
        return openedAt;
    }

    public UUID openedBy() {
        return openedBy;
    }
}
