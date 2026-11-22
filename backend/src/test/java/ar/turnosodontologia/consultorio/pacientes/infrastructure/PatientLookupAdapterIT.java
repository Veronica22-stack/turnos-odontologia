package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.pacientes.domain.Patient;
import ar.turnosodontologia.consultorio.pacientes.domain.PatientSource;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Puerto de consulta de paciente para otros modulos: solo existencia y estado, sin datos personales ni auditoria. */
class PatientLookupAdapterIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private final SpringDataPatientRepository repository;
    private final PatientLookup lookup;

    PatientLookupAdapterIT(SpringDataPatientRepository repository, PatientLookup lookup) {
        this.repository = repository;
        this.lookup = lookup;
    }

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM patient WHERE dni IN ('71000001', '71000002')");
        }
    }

    @Test
    void pacienteActivoExisteYEstaActivo() {
        Patient saved = repository.saveAndFlush(patient("71000001"));

        Optional<PatientStatus> status = lookup.findStatus(saved.id());

        assertThat(status).contains(new PatientStatus(saved.id(), true));
    }

    @Test
    void pacienteDadoDeBajaExistePeroEstaInactivo() {
        Patient inactive = patient("71000002");
        inactive.deactivate();
        Patient saved = repository.saveAndFlush(inactive);

        assertThat(lookup.findStatus(saved.id())).contains(new PatientStatus(saved.id(), false));
    }

    @Test
    void pacienteInexistenteDevuelveVacio() {
        assertThat(lookup.findStatus(UUID.randomUUID())).isEmpty();
    }

    @Test
    void pacienteDeOtraInstalacionDevuelveVacio() {
        Patient saved = repository.saveAndFlush(patient("71000001"));
        InstallationPractice otherInstallation = mock(InstallationPractice.class);
        when(otherInstallation.id()).thenReturn(UUID.randomUUID());

        PatientLookup foreign = new PatientLookupAdapter(repository, otherInstallation);

        assertThat(foreign.findStatus(saved.id())).isEmpty();
    }

    @Test
    void laConsultaNoRegistraPatientRead() throws SQLException {
        Patient saved = repository.saveAndFlush(patient("71000001"));

        lookup.findStatus(saved.id());

        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        "SELECT count(*) FROM audit_log WHERE patient_id = '" + saved.id() + "'")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
    }

    private static Patient patient(String dni) {
        return Patient.create(PRACTICE_ID, "Ana", "Lookup", dni, "1111", null, null, null, PatientSource.MANUAL);
    }
}
