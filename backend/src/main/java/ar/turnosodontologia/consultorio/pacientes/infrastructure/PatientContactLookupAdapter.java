package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import ar.turnosodontologia.consultorio.pacientes.api.PatientContactLookup;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador del puerto {@link PatientContactLookup}: filtra por el consultorio, sin auditoria ni logs. */
@Component
class PatientContactLookupAdapter implements PatientContactLookup {

    private final PatientRepository patientRepository;
    private final InstallationPractice installationPractice;

    PatientContactLookupAdapter(PatientRepository patientRepository, InstallationPractice installationPractice) {
        this.patientRepository = patientRepository;
        this.installationPractice = installationPractice;
    }

    @Override
    public Optional<String> findEmail(UUID patientId) {
        UUID practiceId = installationPractice.id();
        return patientRepository.findById(patientId)
                .filter(patient -> patient.practiceId().equals(practiceId))
                .map(patient -> patient.email())
                .filter(email -> !email.isBlank());
    }
}
