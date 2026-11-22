package ar.turnosodontologia.consultorio.pacientes.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.util.Optional;
import java.util.UUID;

/**
 * Busca un paciente por DNI dentro del consultorio. Si no existe, lo crea.
 * Si ya existe, NO sobreescribe los datos existentes (RN-PA-03).
 * Devuelve el paciente encontrado o creado y un flag indicando si hubo datos divergentes.
 */
public class FindOrCreatePatientByDni {

    private final PatientRepository patientRepository;

    public FindOrCreatePatientByDni(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    public Result execute(UUID practiceId, String rawDni, String firstName, String lastName,
                          String phone, String email, PatientSource source) {
        String normalizedDni = Patient.normalizeAndValidateDni(rawDni);

        Optional<Patient> existing = patientRepository.findByPracticeIdAndDni(practiceId, normalizedDni);

        if (existing.isPresent()) {
            Patient patient = existing.get();
            boolean divergent = hasDivergentData(patient, firstName, lastName, phone, email);
            return new Result(patient, false, divergent);
        }

        Patient newPatient = Patient.create(practiceId, firstName, lastName, rawDni, phone, email, null, null, source);
        Patient saved = patientRepository.save(newPatient);
        return new Result(saved, true, false);
    }

    private static boolean hasDivergentData(Patient existing, String firstName, String lastName,
                                            String phone, String email) {
        boolean namesDiffer = !existing.firstName().equalsIgnoreCase(firstName != null ? firstName.trim() : "")
                || !existing.lastName().equalsIgnoreCase(lastName != null ? lastName.trim() : "");
        boolean phoneDiffers = phone != null && !phone.trim().isEmpty()
                && !existing.phone().equals(phone.trim());
        boolean emailDiffers = email != null && !email.trim().isEmpty()
                && existing.email() != null
                && !existing.email().equalsIgnoreCase(email.trim());
        return namesDiffer || phoneDiffers || emailDiffers;
    }

    public record Result(Patient patient, boolean created, boolean divergentData) {
    }
}
