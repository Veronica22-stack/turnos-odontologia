package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientSource;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PatientRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final SpringDataPatientRepository repository;

    PatientRepositoryIT(SpringDataPatientRepository repository) {
        this.repository = repository;
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM patient");
        }
    }

    @Test
    void guardaYBuscaPorDniNormalizado() {
        Patient saved = repository.saveAndFlush(patient("Juan", "Perez", uniqueDni(), "11223344"));

        Patient found = repository.findByPracticeIdAndDni(PRACTICE_ID, saved.dni()).orElseThrow();

        assertThat(found.id()).isEqualTo(saved.id());
        assertThat(found.dni()).matches("\\d{8}");
        assertThat(found.source()).isEqualTo(PatientSource.MANUAL);
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void buscaPorNombreApellidoDniYTelefonoIgnorandoMayusculas() {
        String dni = uniqueDni();
        Patient saved = repository.saveAndFlush(patient("Juan Carlos", "Perez", dni, "11223344"));

        assertThat(search("juan")).extracting(Patient::id).containsExactly(saved.id());
        assertThat(search("PEREZ")).extracting(Patient::id).containsExactly(saved.id());
        assertThat(search(dni)).extracting(Patient::id).containsExactly(saved.id());
        assertThat(search("1122")).extracting(Patient::id).containsExactly(saved.id());
    }

    @Test
    void filtroOnlyActiveExcluyePacientesDadosDeBaja() {
        Patient active = repository.saveAndFlush(patient("Juan", "Perez", uniqueDni(), "111111"));
        Patient inactive = repository.saveAndFlush(patient("Ana", "Lopez", uniqueDni(), "222222"));
        inactive.deactivate();
        repository.saveAndFlush(inactive);

        assertThat(repository.searchByPracticeId(PRACTICE_ID, "", false))
                .extracting(Patient::id).containsExactlyInAnyOrder(active.id(), inactive.id());
        assertThat(repository.searchByPracticeId(PRACTICE_ID, "", true))
                .extracting(Patient::id).containsExactly(active.id());
    }

    @Test
    void dniDuplicadoEnElMismoConsultorioEsRechazado() {
        String dni = uniqueDni();
        repository.saveAndFlush(patient("Juan", "Perez", dni, "111111"));

        assertThatThrownBy(() -> repository.saveAndFlush(patient("Pedro", "Gomez", dni, "222222")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("patient_practice_dni_unique");
    }

    private List<Patient> search(String term) {
        return repository.searchByPracticeId(PRACTICE_ID, term, true);
    }

    private static Patient patient(String firstName, String lastName, String dni, String phone) {
        return Patient.create(PRACTICE_ID, firstName, lastName, dni, phone, null, null, null, PatientSource.MANUAL);
    }

    private static String uniqueDni() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
    }
}
