package ar.turnosodontologia.consultorio.pacientes.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientSource;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientsService {

    private final PatientRepository patientRepository;
    private final InstallationPractice installationPractice;
    private final AuditLogger auditLogger;

    public PatientsService(
            PatientRepository patientRepository,
            InstallationPractice installationPractice,
            AuditLogger auditLogger) {
        this.patientRepository = patientRepository;
        this.installationPractice = installationPractice;
        this.auditLogger = auditLogger;
    }

    @Transactional(readOnly = true)
    public List<PatientSummaryData> searchPatients(String term, boolean onlyActive) {
        UUID practiceId = installationPractice.id();
        String safeTerm = term != null ? term.trim() : "";
        return patientRepository.searchByPracticeId(practiceId, safeTerm, onlyActive).stream()
                .map(p -> new PatientSummaryData(p.id(), p.firstName(), p.lastName(), p.dni(), p.phone(), p.isActive()))
                .toList();
    }

    // El evento PATIENT_READ se persiste en la misma transaccion: no puede ser readOnly.
    @Transactional
    public PatientData getPatientById(UUID patientId, UUID userId) {
        Patient patient = findOrThrow(patientId);
        auditLogger.log(AuditEvent.of(
                AuditAction.PATIENT_READ, userId, patientId,
                "patient", patientId.toString(), null, null, Map.of()));
        return PatientData.from(patient);
    }

    @Transactional
    public PatientData createPatient(
            String firstName, String lastName, String dni, String phone,
            String email, LocalDate birthDate, String address) {
        UUID practiceId = installationPractice.id();
        Patient patient = Patient.create(
                practiceId, firstName, lastName, dni, phone, email, birthDate, address, PatientSource.MANUAL);
        try {
            Patient saved = patientRepository.save(patient);
            return PatientData.from(saved);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("patient_practice_dni_unique")) {
                throw new ConflictException("DNI_ALREADY_EXISTS");
            }
            throw ex;
        }
    }

    @Transactional
    public PatientData updatePatient(
            UUID patientId, UUID userId, String firstName, String lastName,
            String phone, String email, LocalDate birthDate, String address) {
        Patient patient = findOrThrow(patientId);
        patient.update(firstName, lastName, phone, email, birthDate, address);
        Patient saved = patientRepository.save(patient);
        auditLogger.log(AuditEvent.of(
                AuditAction.PATIENT_UPDATE, userId, patientId,
                "patient", patientId.toString(), null, null, Map.of()));
        return PatientData.from(saved);
    }

    @Transactional
    public void deactivatePatient(UUID patientId) {
        Patient patient = findOrThrow(patientId);
        patient.deactivate();
        patientRepository.save(patient);
    }

    private Patient findOrThrow(UUID patientId) {
        UUID practiceId = installationPractice.id();
        return patientRepository.findById(patientId)
                .filter(p -> p.practiceId().equals(practiceId))
                .orElseThrow(() -> new NotFoundException("PATIENT_NOT_FOUND"));
    }
}
