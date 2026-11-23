package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.agenda.api.ActiveBookingsLookup;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** El límite por contacto cuenta solo turnos futuros CONFIRMED, nunca cancelados ni pasados (RN-RS-08). */
class ActiveBookingsLookupIT extends AbstractIntegrationTest {

    @Autowired private ActiveBookingsLookup lookup;

    private UUID patientId;
    private UUID serviceId;

    @BeforeEach
    void setUp() {
        patientId = AppointmentFixtures.patient(true);
        serviceId = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void cleanUp() {
        AppointmentFixtures.cleanAll();
    }

    @Test
    void soloCuentaTurnosFuturosConfirmados() {
        AppointmentFixtures.appointment(patientId, serviceId,
                "2099-12-10T13:00:00Z", "2099-12-10T13:30:00Z", "CONFIRMED");
        AppointmentFixtures.appointment(patientId, serviceId,
                "2099-12-11T13:00:00Z", "2099-12-11T13:30:00Z", "CANCELLED");
        AppointmentFixtures.appointment(patientId, serviceId,
                "2020-12-10T13:00:00Z", "2020-12-10T13:30:00Z", "CONFIRMED");

        long count = lookup.countActiveFuture(
                UUID.fromString(AppointmentFixtures.PRACTICE_ID), List.of(patientId), Instant.now());

        assertThat(count).isEqualTo(1);
    }

    @Test
    void listaVaciaDePacientesDevuelveCero() {
        assertThat(lookup.countActiveFuture(
                UUID.fromString(AppointmentFixtures.PRACTICE_ID), List.of(), Instant.now())).isZero();
    }
}
