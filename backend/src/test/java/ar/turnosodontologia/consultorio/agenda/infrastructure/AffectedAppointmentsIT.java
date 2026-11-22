package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentSlot;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentsProvider;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Escenarios "Con proveedor de turnos" y "Turnos excluidos" del contrato de turnos afectados (C-09 / D12). */
class AffectedAppointmentsIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE = UUID.fromString(AppointmentFixtures.PRACTICE_ID);
    private static final UUID OTHER_PRACTICE = UUID.fromString("01900000-0000-7000-8000-000000000009");

    private final AffectedAppointments publicContract;
    private final AffectedAppointmentsProvider domainPort;
    private UUID patient;
    private UUID service;

    AffectedAppointmentsIT(AffectedAppointments publicContract, AffectedAppointmentsProvider domainPort) {
        this.publicContract = publicContract;
        this.domainPort = domainPort;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        service = AppointmentFixtures.service(30, true, true);
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static Instant at(String time) {
        return Instant.parse("2099-12-10T" + time + ":00Z");
    }

    private UUID insert(String start, String end, String status) {
        return AppointmentFixtures.appointment(patient, service, at(start).toString(), at(end).toString(), status);
    }

    @Test
    void conProveedorDevuelveElTurnoConfirmadoConIdEInstantesSinModificarlo() {
        UUID id = insert("10:00", "10:30", "CONFIRMED");

        List<AffectedAppointment> result = publicContract.findWithin(PRACTICE, at("09:00"), at("12:00"));

        assertThat(result).containsExactly(new AffectedAppointment(id, at("10:00"), at("10:30")));
        assertThat(domainPort.findWithin(PRACTICE, at("09:00"), at("12:00")))
                .containsExactly(new AffectedAppointmentSlot(id, at("10:00"), at("10:30")));
        // El turno sigue confirmado: consultar no lo modifica.
        assertThat(publicContract.findWithin(PRACTICE, at("09:00"), at("12:00"))).hasSize(1);
    }

    @Test
    void devuelveLosTurnosOrdenadosPorInicio() {
        UUID late = insert("11:00", "11:30", "CONFIRMED");
        UUID early = insert("09:00", "09:30", "CONFIRMED");

        assertThat(publicContract.findWithin(PRACTICE, at("08:00"), at("12:00")))
                .extracting(AffectedAppointment::appointmentId).containsExactly(early, late);
    }

    @Test
    void excluyeCanceladosCompletadosAdyacentesYDeOtroConsultorio() {
        insert("09:30", "10:00", "CANCELLED");
        insert("10:00", "10:30", "COMPLETED");
        insert("10:30", "11:00", "NO_SHOW");
        insert("12:00", "12:30", "CONFIRMED");

        assertThat(publicContract.findWithin(PRACTICE, at("09:00"), at("12:00"))).isEmpty();
        assertThat(publicContract.findWithin(OTHER_PRACTICE, at("09:00"), at("13:00"))).isEmpty();
    }

    @Test
    void incluyeElTurnoQueCruzaElBordeDelRango() {
        UUID crossing = insert("11:45", "12:15", "CONFIRMED");

        assertThat(publicContract.findWithin(PRACTICE, at("09:00"), at("12:00")))
                .extracting(AffectedAppointment::appointmentId).containsExactly(crossing);
    }
}
