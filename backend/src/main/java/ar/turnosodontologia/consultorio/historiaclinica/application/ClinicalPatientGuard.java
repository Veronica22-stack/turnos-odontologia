package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Valida el paciente antes de tocar la historia clinica: existe para leer, y ademas esta activo para escribir. */
@Component
public class ClinicalPatientGuard {

    private final PatientLookup patients;

    public ClinicalPatientGuard(PatientLookup patients) {
        this.patients = patients;
    }

    public PatientStatus requireReadable(UUID patientId) {
        return patients.findStatus(patientId).orElseThrow(() -> new NotFoundException("PATIENT_NOT_FOUND"));
    }

    public PatientStatus requireWritable(UUID patientId) {
        PatientStatus status = requireReadable(patientId);
        if (!status.active()) {
            throw new ConflictException("PATIENT_INACTIVE");
        }
        return status;
    }
}
