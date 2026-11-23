package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.pacientes.api.PatientContactLookup;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Email de contacto para notificaciones: solo el email, sin auditoria ni log (design D10). */
@ExtendWith(OutputCaptureExtension.class)
class PatientContactLookupIT extends AbstractIntegrationTest {

    private static final String EMAIL = "ana.contacto@ejemplo.com";

    private final PatientContactLookup lookup;
    private final SpringDataPatientRepository patients;

    PatientContactLookupIT(PatientContactLookup lookup, SpringDataPatientRepository patients) {
        this.lookup = lookup;
        this.patients = patients;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void pacienteConEmailDevuelveSoloElEmail() {
        UUID patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + EMAIL + "' WHERE id = '" + patient + "'");

        assertThat(lookup.findEmail(patient)).contains(EMAIL);
    }

    @Test
    void pacienteSinEmailOInexistenteDevuelveVacioSinError() {
        UUID withoutEmail = AppointmentFixtures.patient(true);

        assertThat(lookup.findEmail(withoutEmail)).isEmpty();
        assertThat(lookup.findEmail(UUID.randomUUID())).isEmpty();
    }

    @Test
    void pacienteInactivoConEmailDevuelveElEmail() {
        UUID inactive = AppointmentFixtures.patient(false);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + EMAIL + "' WHERE id = '" + inactive + "'");

        assertThat(lookup.findEmail(inactive)).contains(EMAIL);
    }

    @Test
    void pacienteDeOtraInstalacionDevuelveVacio() {
        UUID patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + EMAIL + "' WHERE id = '" + patient + "'");
        InstallationPractice otherInstallation = mock(InstallationPractice.class);
        when(otherInstallation.id()).thenReturn(UUID.randomUUID());

        PatientContactLookup foreign = new PatientContactLookupAdapter(patients, otherInstallation);

        assertThat(foreign.findEmail(patient)).isEmpty();
    }

    @Test
    void laConsultaNoAgregaFilasDeAuditoriaNiLoguea(CapturedOutput output) throws SQLException {
        UUID patient = AppointmentFixtures.patient(true);
        AppointmentFixtures.execute("UPDATE patient SET email = '" + EMAIL + "' WHERE id = '" + patient + "'");
        int before = auditRows();

        lookup.findEmail(patient);

        assertThat(auditRows()).isEqualTo(before);
        assertThat(output.getAll()).doesNotContain(EMAIL);
    }

    private static int auditRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT count(*) FROM audit_log")) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
