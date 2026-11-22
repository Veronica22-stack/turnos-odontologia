package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAnamnesisEntryRepository extends JpaRepository<AnamnesisEntry, UUID> {

    Optional<AnamnesisEntry> findByIdAndClinicalRecordId(UUID id, UUID clinicalRecordId);

    boolean existsByCorrectsEntryId(UUID correctsEntryId);

    List<AnamnesisEntry> findByClinicalRecordIdOrderByVersionDesc(UUID clinicalRecordId);

    @Query("SELECT COALESCE(MAX(a.version), 0) FROM AnamnesisEntry a WHERE a.clinicalRecordId = :recordId")
    int maxVersion(@Param("recordId") UUID recordId);
}
