package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataOdontogramEntryRepository extends JpaRepository<OdontogramEntry, UUID> {

    Optional<OdontogramEntry> findByIdAndClinicalRecordId(UUID id, UUID clinicalRecordId);

    boolean existsByCorrectsEntryId(UUID correctsEntryId);

    @Query("SELECT e FROM OdontogramEntry e WHERE e.clinicalRecordId = :recordId ORDER BY e.createdAt DESC, e.id DESC")
    List<OdontogramEntry> findNewestFirst(@Param("recordId") UUID recordId);

    @Query("""
            SELECT COUNT(e) > 0 FROM OdontogramEntry e
            WHERE e.clinicalRecordId = :recordId AND e.toothCode = :toothCode AND e.surface IS NULL
              AND (e.createdAt > :createdAt OR (e.createdAt = :createdAt AND e.id > :id))""")
    boolean existsNewerWholeTooth(
            @Param("recordId") UUID recordId, @Param("toothCode") String toothCode,
            @Param("createdAt") Instant createdAt, @Param("id") UUID id);

    @Query("""
            SELECT COUNT(e) > 0 FROM OdontogramEntry e
            WHERE e.clinicalRecordId = :recordId AND e.toothCode = :toothCode AND e.surface = :surface
              AND (e.createdAt > :createdAt OR (e.createdAt = :createdAt AND e.id > :id))""")
    boolean existsNewerOnSurface(
            @Param("recordId") UUID recordId, @Param("toothCode") String toothCode,
            @Param("surface") ToothSurface surface, @Param("createdAt") Instant createdAt, @Param("id") UUID id);
}
