package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Consulta de turno para notificaciones: solo lectura, sin notas, acotada al consultorio (design D10). */
class AppointmentNotificationLookupIT extends AbstractIntegrationTest {

    private final AppointmentNotificationLookup lookup;
    private final SpringDataAppointmentRepository appointments;
    private final SpringDataPracticeServiceRepository services;
    private UUID patient;
    private UUID serviceId;

    AppointmentNotificationLookupIT(AppointmentNotificationLookup lookup, SpringDataAppointmentRepository appointments,
            SpringDataPracticeServiceRepository services) {
        this.lookup = lookup;
        this.appointments = appointments;
        this.services = services;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        serviceId = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void turnoConfirmadoDevuelveEstadoPacientePrestacionEInstantesSinNotas() throws SQLException {
        UUID id = AppointmentFixtures.appointment(patient, serviceId, "2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z",
                "CONFIRMED");
        AppointmentFixtures.execute("UPDATE appointment SET notes = 'nota privada' WHERE id = '" + id + "'");

        Optional<AppointmentNotificationView> view = lookup.find(id);

        assertThat(view).isPresent();
        assertThat(view.get().appointmentId()).isEqualTo(id);
        assertThat(view.get().patientId()).isEqualTo(patient);
        assertThat(view.get().status()).isEqualTo(AppointmentStatusView.CONFIRMED);
        assertThat(view.get().startsAt()).isEqualTo(Instant.parse("2026-12-10T13:00:00Z"));
        assertThat(view.get().endsAt()).isEqualTo(Instant.parse("2026-12-10T13:30:00Z"));
        assertThat(view.get().serviceName()).isEqualTo(serviceName(serviceId));
        assertThat(view.get().toString()).doesNotContain("nota privada");
    }

    @Test
    void turnoCanceladoDevuelveEstadoCanceladoConSusInstantesOriginales() {
        UUID id = AppointmentFixtures.appointment(patient, serviceId, "2026-12-11T13:00:00Z", "2026-12-11T13:30:00Z",
                "CANCELLED");

        Optional<AppointmentNotificationView> view = lookup.find(id);

        assertThat(view).isPresent();
        assertThat(view.get().status()).isEqualTo(AppointmentStatusView.CANCELLED);
        assertThat(view.get().startsAt()).isEqualTo(Instant.parse("2026-12-11T13:00:00Z"));
        assertThat(view.get().endsAt()).isEqualTo(Instant.parse("2026-12-11T13:30:00Z"));
    }

    @Test
    void elNombreDeLaPrestacionEsElVigente() {
        UUID id = AppointmentFixtures.appointment(patient, serviceId, "2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z",
                "CONFIRMED");
        AppointmentFixtures.execute("UPDATE service SET name = 'IT-APPT-Renombrada' WHERE id = '" + serviceId + "'");

        assertThat(lookup.find(id)).get().extracting(AppointmentNotificationView::serviceName)
                .isEqualTo("IT-APPT-Renombrada");
    }

    @Test
    void turnoInexistenteDevuelveVacio() {
        assertThat(lookup.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void turnoDeOtroConsultorioDevuelveVacio() {
        UUID id = AppointmentFixtures.appointment(patient, serviceId, "2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z",
                "CONFIRMED");
        InstallationPractice otherInstallation = mock(InstallationPractice.class);
        when(otherInstallation.id()).thenReturn(UUID.randomUUID());

        AppointmentNotificationLookup foreign =
                new AppointmentNotificationLookupAdapter(appointments, services, otherInstallation);

        assertThat(foreign.find(id)).isEmpty();
        assertThat(lookup.find(id)).isPresent();
    }

    @Test
    void laConsultaNoModificaElTurno() throws SQLException {
        UUID id = AppointmentFixtures.appointment(patient, serviceId, "2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z",
                "CONFIRMED");
        Instant before = updatedAt(id);

        lookup.find(id);

        assertThat(updatedAt(id)).isEqualTo(before);
    }

    private static Instant updatedAt(UUID id) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT updated_at FROM appointment WHERE id = '" + id + "'")) {
            rows.next();
            return rows.getTimestamp(1).toInstant();
        }
    }

    private static String serviceName(UUID id) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT name FROM service WHERE id = '" + id + "'")) {
            rows.next();
            return rows.getString(1);
        }
    }
}
