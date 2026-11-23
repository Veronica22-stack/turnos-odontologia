package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import ar.turnosodontologia.consultorio.pacientes.api.PatientBookingOperations;
import ar.turnosodontologia.consultorio.pacientes.api.PatientBookingResult;
import ar.turnosodontologia.consultorio.pacientes.domain.FindOrCreatePatientByDni;
import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientRepository;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientSource;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Fachada de {@link FindOrCreatePatientByDni} en la api del módulo, exigida por el grafo de arquitectura. */
@Component
class PatientBookingOperationsAdapter implements PatientBookingOperations {

    private final FindOrCreatePatientByDni findOrCreate;
    private final SpringDataPatientRepository patients;

    PatientBookingOperationsAdapter(PatientRepository repository, SpringDataPatientRepository patients) {
        this.findOrCreate = new FindOrCreatePatientByDni(repository);
        this.patients = patients;
    }

    @Override
    public PatientBookingResult findOrCreate(UUID practiceId, String dni, String firstName, String lastName,
            String phone, String email) {
        FindOrCreatePatientByDni.Result result = findOrCreate.execute(
                practiceId, dni, firstName, lastName, phone, email, PatientSource.PUBLIC_BOOKING);
        return new PatientBookingResult(result.patient().id(), result.created(), result.divergentData());
    }

    @Override
    public List<UUID> findIdsByContact(UUID practiceId, String dni, String email, String phone) {
        String normalizedDni = Patient.normalizeAndValidateDni(dni);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedPhone = phone == null ? "" : phone.trim();
        return patients.findIdsByContact(practiceId, normalizedDni, normalizedEmail, normalizedPhone);
    }
}
