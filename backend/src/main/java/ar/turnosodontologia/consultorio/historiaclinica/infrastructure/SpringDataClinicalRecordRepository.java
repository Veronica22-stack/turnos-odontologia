package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataClinicalRecordRepository extends JpaRepository<ClinicalRecord, UUID> {

    Optional<ClinicalRecord> findByPatientId(UUID patientId);

    /** Alta idempotente: si ya existe la historia del paciente no hace nada (sin UPDATE, sin locks de fila). */
    @Modifying
    @Query(value = """
            INSERT INTO clinical_record (id, practice_id, patient_id, opened_at, opened_by)
            VALUES (:id, :practiceId, :patientId, :openedAt, :openedBy)
            ON CONFLICT (patient_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("practiceId") UUID practiceId,
            @Param("patientId") UUID patientId,
            @Param("openedAt") Instant openedAt,
            @Param("openedBy") UUID openedBy);
}
