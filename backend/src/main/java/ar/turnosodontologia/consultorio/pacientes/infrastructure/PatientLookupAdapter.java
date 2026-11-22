package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador del puerto {@link PatientLookup}: filtra por el consultorio de la instalacion. */
@Component
class PatientLookupAdapter implements PatientLookup {

    private final PatientRepository patientRepository;
    private final InstallationPractice installationPractice;

    PatientLookupAdapter(PatientRepository patientRepository, InstallationPractice installationPractice) {
        this.patientRepository = patientRepository;
        this.installationPractice = installationPractice;
    }

    @Override
    public Optional<PatientStatus> findStatus(UUID patientId) {
        UUID practiceId = installationPractice.id();
        return patientRepository.findById(patientId)
                .filter(patient -> patient.practiceId().equals(practiceId))
                .map(patient -> new PatientStatus(patient.id(), patient.isActive()));
    }
}
