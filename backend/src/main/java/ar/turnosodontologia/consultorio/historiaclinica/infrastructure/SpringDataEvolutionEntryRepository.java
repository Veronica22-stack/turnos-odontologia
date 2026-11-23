package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEvolutionEntryRepository extends JpaRepository<EvolutionEntry, UUID> {

    Optional<EvolutionEntry> findByIdAndClinicalRecordId(UUID id, UUID clinicalRecordId);

    boolean existsByCorrectsEntryId(UUID correctsEntryId);

    @Query("SELECT e FROM EvolutionEntry e WHERE e.clinicalRecordId = :recordId ORDER BY e.createdAt DESC, e.id DESC")
    List<EvolutionEntry> findNewestFirst(@Param("recordId") UUID recordId);
}
