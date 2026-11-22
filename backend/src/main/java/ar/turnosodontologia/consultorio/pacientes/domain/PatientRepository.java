package ar.turnosodontologia.consultorio.pacientes.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository {

    Optional<Patient> findById(UUID id);

    Optional<Patient> findByPracticeIdAndDni(UUID practiceId, String dni);

    List<Patient> searchByPracticeId(UUID practiceId, String term, boolean onlyActive);

    <S extends Patient> S save(S entity);
}
