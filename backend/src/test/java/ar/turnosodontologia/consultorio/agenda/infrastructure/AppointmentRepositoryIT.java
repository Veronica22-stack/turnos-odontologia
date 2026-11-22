package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE = UUID.fromString(AppointmentFixtures.PRACTICE_ID);
    private static final UUID OTHER_PRACTICE = UUID.fromString("01900000-0000-7000-8000-000000000009");

    private final AppointmentRepository repository;
    private UUID patient;
    private UUID service;

    AppointmentRepositoryIT(AppointmentRepository repository) {
        this.repository = repository;
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

    // ---- findOverlappingActive ----

    @Test
    void findOverlappingActiveDevuelveSoloNoCanceladosQueSolapanConIntervaloSemiabierto() {
        UUID confirmed = insert("10:00", "10:30", "CONFIRMED");
        UUID completed = insert("11:00", "11:30", "COMPLETED");
        UUID noShow = insert("12:00", "12:30", "NO_SHOW");
        insert("13:00", "13:30", "CANCELLED");
        insert("14:00", "14:30", "CONFIRMED");

        List<Appointment> found = repository.findOverlappingActive(PRACTICE, at("10:15"), at("12:15"), null);

        assertThat(found).extracting(Appointment::id).containsExactlyInAnyOrder(confirmed, completed, noShow);
    }

    @Test
    void findOverlappingActiveTrataLaAdyacenciaComoNoSolapamiento() {
        UUID first = insert("10:00", "10:30", "CONFIRMED");

        assertThat(repository.findOverlappingActive(PRACTICE, at("10:30"), at("11:00"), null)).isEmpty();
        assertThat(repository.findOverlappingActive(PRACTICE, at("09:30"), at("10:00"), null)).isEmpty();
        assertThat(repository.findOverlappingActive(PRACTICE, at("10:29"), at("10:31"), null))
                .extracting(Appointment::id).containsExactly(first);
    }

    @Test
    void findOverlappingActiveExcluyeElIdIndicado() {
        UUID self = insert("10:00", "10:30", "CONFIRMED");
        UUID other = insert("10:30", "11:00", "CONFIRMED");

        assertThat(repository.findOverlappingActive(PRACTICE, at("10:00"), at("11:00"), self))
                .extracting(Appointment::id).containsExactly(other);
        assertThat(repository.findOverlappingActive(PRACTICE, at("10:00"), at("10:30"), self)).isEmpty();
    }

    @Test
    void findOverlappingActiveFiltraPorConsultorio() {
        insert("10:00", "10:30", "CONFIRMED");

        assertThat(repository.findOverlappingActive(OTHER_PRACTICE, at("10:00"), at("10:30"), null)).isEmpty();
    }

    // ---- findConfirmedWithin ----

    @Test
    void findConfirmedWithinDevuelveSoloConfirmadosOrdenadosPorInicio() {
        UUID late = insert("11:00", "11:30", "CONFIRMED");
        UUID early = insert("09:30", "10:00", "CONFIRMED");
        insert("10:00", "10:30", "CANCELLED");
        insert("10:30", "11:00", "COMPLETED");
        insert("08:00", "08:30", "NO_SHOW");

        List<Appointment> found = repository.findConfirmedWithin(PRACTICE, at("09:00"), at("12:00"));

        assertThat(found).extracting(Appointment::id).containsExactly(early, late);
    }

    @Test
    void findConfirmedWithinIncluyeElQueCruzaElBordeYExcluyeAdyacentesYOtroConsultorio() {
        UUID crossing = insert("11:45", "12:15", "CONFIRMED");
        insert("12:15", "12:30", "CONFIRMED");
        insert("08:30", "09:00", "CONFIRMED");

        assertThat(repository.findConfirmedWithin(PRACTICE, at("09:00"), at("12:00")))
                .extracting(Appointment::id).containsExactly(crossing);
        assertThat(repository.findConfirmedWithin(OTHER_PRACTICE, at("09:00"), at("13:00"))).isEmpty();
    }

    // ---- findInRange ----

    @Test
    void findInRangeDevuelveTodosLosEstadosQueIntersectanOrdenadosPorInicio() {
        UUID cancelled = insert("10:00", "10:30", "CANCELLED");
        UUID crossingStart = insert("08:45", "09:15", "CONFIRMED");
        UUID completed = insert("09:30", "10:00", "COMPLETED");
        UUID noShow = insert("11:00", "11:30", "NO_SHOW");
        insert("12:00", "12:30", "CONFIRMED");
        insert("08:00", "08:45", "CONFIRMED");

        List<Appointment> found = repository.findInRange(PRACTICE, at("09:00"), at("12:00"));

        assertThat(found).extracting(Appointment::id)
                .containsExactly(crossingStart, completed, cancelled, noShow);
    }

    @Test
    void findInRangeFiltraPorConsultorio() {
        insert("10:00", "10:30", "CONFIRMED");

        assertThat(repository.findInRange(OTHER_PRACTICE, at("09:00"), at("12:00"))).isEmpty();
    }
}
