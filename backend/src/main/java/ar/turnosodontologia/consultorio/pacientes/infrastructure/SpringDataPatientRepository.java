package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPatientRepository extends JpaRepository<Patient, UUID>, PatientRepository {

    @Override
    Optional<Patient> findByPracticeIdAndDni(UUID practiceId, String dni);

    @Override
    @Query("""
            SELECT p FROM Patient p
            WHERE p.practiceId = :practiceId
            AND (:onlyActive = false OR p.active = true)
            AND (
                LOWER(p.firstName) LIKE LOWER(CONCAT('%', :term, '%'))
                OR LOWER(p.lastName) LIKE LOWER(CONCAT('%', :term, '%'))
                OR p.dni LIKE CONCAT('%', :term, '%')
                OR p.phone LIKE CONCAT('%', :term, '%')
            )
            ORDER BY p.lastName, p.firstName
            """)
    List<Patient> searchByPracticeId(
            @Param("practiceId") UUID practiceId,
            @Param("term") String term,
            @Param("onlyActive") boolean onlyActive);
}
