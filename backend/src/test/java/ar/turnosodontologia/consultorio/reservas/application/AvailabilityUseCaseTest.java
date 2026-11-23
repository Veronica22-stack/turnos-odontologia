package ar.turnosodontologia.consultorio.reservas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.api.BlockedInterval;
import ar.turnosodontologia.consultorio.agenda.api.BlockedRanges;
import ar.turnosodontologia.consultorio.agenda.api.PublicService;
import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.agenda.api.WeeklyInterval;
import ar.turnosodontologia.consultorio.agenda.api.WorkingHoursLookup;
import ar.turnosodontologia.consultorio.reservas.domain.Audience;
import ar.turnosodontologia.consultorio.reservas.domain.Interval;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettings;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettingsLookup;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityUseCaseTest {

    private static final UUID PRACTICE = UUID.randomUUID();
    private static final UUID SERVICE = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 3, 10);
    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-03-10T02:00:00Z");

    @Mock private WorkingHoursLookup workingHours;
    @Mock private ServiceAvailabilityLookup services;
    @Mock private PracticeBookingSettingsLookup settings;
    @Mock private AffectedAppointments appointments;
    @Mock private BlockedRanges blockedRanges;

    @Test
    void consultaPublicaDevuelveSlotsEnUtc() {
        givenCatalogoConPrestacion(true, true);
        givenHorariosSemana(new WeeklyInterval(2, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        givenSinOcupantes();

        AvailabilityOutcome outcome = useCase(0, 60).findAvailability(query(Audience.PUBLIC));

        assertThat(outcome.noActiveServices()).isFalse();
        assertThat(startsOf(outcome)).containsExactly(
                Instant.parse("2026-03-10T12:00:00Z"),
                Instant.parse("2026-03-10T12:15:00Z"),
                Instant.parse("2026-03-10T12:30:00Z"));
        assertThat(outcome.slots()).allSatisfy(slot -> assertThat(
                Duration.between(slot.start(), slot.end())).isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void catalogoVacioEsEstadoNoErrorYNoResuelveLaPrestacion() {
        when(services.hasPublicServices(PRACTICE)).thenReturn(false);

        AvailabilityOutcome outcome = useCase(0, 60).findAvailability(query(Audience.PUBLIC));

        assertThat(outcome.noActiveServices()).isTrue();
        assertThat(outcome.slots()).isEmpty();
        verify(services, never()).findPublicService(PRACTICE, SERVICE);
    }

    @Test
    void serviceIdAjenoEsNotFound() {
        when(services.hasPublicServices(PRACTICE)).thenReturn(true);
        when(services.findPublicService(PRACTICE, SERVICE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase(0, 60).findAvailability(query(Audience.PUBLIC)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("SERVICE_NOT_FOUND");
    }

    @Test
    void prestacionInactivaEsUnprocessableEntity() {
        givenCatalogoConPrestacion(false, true);

        assertThatThrownBy(() -> useCase(0, 60).findAvailability(query(Audience.PUBLIC)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("SERVICE_INACTIVE");
    }

    @Test
    void prestacionSinVisibilidadPublicaEsNotFound() {
        givenCatalogoConPrestacion(true, false);

        assertThatThrownBy(() -> useCase(0, 60).findAvailability(query(Audience.PUBLIC)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("SERVICE_NOT_FOUND");
    }

    @Test
    void vistaInternaNoAplicaFiltrosNiConsultaElCatalogoPublico() {
        givenPrestacion(true, false);
        givenHorariosSemana(new WeeklyInterval(2, LocalTime.of(9, 0), LocalTime.of(11, 0)));

        AvailabilityOutcome outcome = useCase(120, 60).findAvailability(
                new AvailabilityQuery(PRACTICE, SERVICE, LocalDate.of(2027, 1, 5), Audience.INTERNAL));

        assertThat(outcome.noActiveServices()).isFalse();
        assertThat(outcome.slots()).isNotEmpty();
        verify(services, never()).hasPublicServices(PRACTICE);
    }

    @Test
    void fechaFueraDelHorizontePublicoDevuelveSlotsVacios() {
        givenCatalogoConPrestacion(true, true);

        AvailabilityOutcome outcome = useCase(0, 60).findAvailability(query(Audience.PUBLIC).withDate(DATE.plusDays(61)));

        assertThat(outcome.noActiveServices()).isFalse();
        assertThat(outcome.slots()).isEmpty();
    }

    @Test
    void turnosConfirmadosYBloqueosSeRestanDelTramo() {
        givenCatalogoConPrestacion(true, true);
        givenHorariosSemana(new WeeklyInterval(2, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        when(appointments.findWithin(PRACTICE, Instant.parse("2026-03-10T03:00:00Z"),
                Instant.parse("2026-03-11T03:00:00Z")))
                .thenReturn(List.of(new AffectedAppointment(UUID.randomUUID(),
                        Instant.parse("2026-03-10T12:00:00Z"), Instant.parse("2026-03-10T12:30:00Z"))));
        when(blockedRanges.findOverlapping(PRACTICE, Instant.parse("2026-03-10T03:00:00Z"),
                Instant.parse("2026-03-11T03:00:00Z")))
                .thenReturn(List.of(new BlockedInterval(UUID.randomUUID(),
                        Instant.parse("2026-03-10T13:30:00Z"), Instant.parse("2026-03-10T14:00:00Z"))));

        AvailabilityOutcome outcome = useCase(0, 60).findAvailability(query(Audience.PUBLIC));

        assertThat(startsOf(outcome)).containsExactly(
                Instant.parse("2026-03-10T12:30:00Z"),
                Instant.parse("2026-03-10T12:45:00Z"),
                Instant.parse("2026-03-10T13:00:00Z"));
    }

    @Test
    void sinTramosParaElDiaSolicitadoDevuelveListaVacia() {
        givenCatalogoConPrestacion(true, true);
        givenHorariosSemana(new WeeklyInterval(3, LocalTime.of(9, 0), LocalTime.of(10, 0)));

        assertThat(useCase(0, 60).findAvailability(query(Audience.PUBLIC)).slots()).isEmpty();
    }

    private AvailabilityUseCase useCase(int minNoticeMin, int maxAdvanceDays) {
        lenient().when(settings.findBookingSettings(PRACTICE)).thenReturn(new PracticeBookingSettings(
                BA.getId(), 15, minNoticeMin, maxAdvanceDays));
        return new AvailabilityUseCase(workingHours, services, settings, appointments, blockedRanges,
                Clock.fixed(NOW, BA));
    }

    private AvailabilityQuery query(Audience audience) {
        return new AvailabilityQuery(PRACTICE, SERVICE, DATE, audience);
    }

    private void givenCatalogoConPrestacion(boolean active, boolean publicVisible) {
        when(services.hasPublicServices(PRACTICE)).thenReturn(true);
        givenPrestacion(active, publicVisible);
    }

    private void givenPrestacion(boolean active, boolean publicVisible) {
        when(services.findPublicService(PRACTICE, SERVICE)).thenReturn(Optional.of(
                new PublicService(SERVICE, Duration.ofMinutes(30), active, publicVisible)));
    }

    private void givenHorariosSemana(WeeklyInterval... intervals) {
        when(workingHours.findWeeklyIntervals(PRACTICE)).thenReturn(List.of(intervals));
    }

    private void givenSinOcupantes() {
        when(appointments.findWithin(PRACTICE, Instant.parse("2026-03-10T03:00:00Z"),
                Instant.parse("2026-03-11T03:00:00Z"))).thenReturn(List.of());
        when(blockedRanges.findOverlapping(PRACTICE, Instant.parse("2026-03-10T03:00:00Z"),
                Instant.parse("2026-03-11T03:00:00Z"))).thenReturn(List.of());
    }

    private static List<Instant> startsOf(AvailabilityOutcome outcome) {
        return outcome.slots().stream().map(Interval::start).toList();
    }
}
