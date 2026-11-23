package ar.turnosodontologia.consultorio.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Fecha local, zona, reloj inyectable y filtros por audiencia. */
class AvailabilityWindowTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void diaLocalDeBuenosAiresSeConvierteAlRangoUtcSemiabierto() {
        AvailabilityWindow window = window(BA, Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), BA),
                Audience.PUBLIC, 120, 60);

        Interval rango = window.localDayInterval(LocalDate.of(2026, 3, 10));

        assertThat(rango.start()).isEqualTo(Instant.parse("2026-03-10T03:00:00Z"));
        assertThat(rango.end()).isEqualTo(Instant.parse("2026-03-11T03:00:00Z"));
    }

    @Test
    void turnoUtcDelDiaSiguientePerteneceAlDiaLocalAnterior() {
        AvailabilityWindow window = window(BA, Clock.fixed(Instant.parse("2026-03-10T12:00:00Z"), BA),
                Audience.PUBLIC, 120, 60);
        Interval rango = window.localDayInterval(LocalDate.of(2026, 3, 10));

        Instant turno = Instant.parse("2026-03-11T02:00:00Z");

        assertThat(turno.isBefore(rango.end())).isTrue();
    }

    @Test
    void horaLocalInexistentePorSaltoDeHorarioNoSeConvierte() {
        ZoneId newYork = ZoneId.of("America/New_York");
        AvailabilityWindow window = window(newYork,
                Clock.fixed(Instant.parse("2026-03-08T12:00:00Z"), newYork),
                Audience.PUBLIC, 0, 60);

        assertThat(window.instantOf(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30))).isEmpty();
    }

    @Test
    void horaLocalAmbiguaUsaElDesfaseMasTemprano() {
        ZoneId newYork = ZoneId.of("America/New_York");
        AvailabilityWindow window = window(newYork,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), newYork),
                Audience.PUBLIC, 0, 60);

        assertThat(window.instantOf(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30)))
                .contains(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void anticipacionMinimaExcluyeIniciosAnterioresAlLimite() {
        Instant now = Instant.parse("2026-03-10T12:00:00Z");
        AvailabilityWindow window = window(BA, Clock.fixed(now, BA), Audience.PUBLIC, 120, 60);
        List<Interval> slots = List.of(
                interval("2026-03-10T13:30:00Z", "2026-03-10T14:00:00Z"),
                interval("2026-03-10T14:00:00Z", "2026-03-10T14:30:00Z"),
                interval("2026-03-10T14:30:00Z", "2026-03-10T15:00:00Z"));

        assertThat(startsOf(window.filter(slots))).containsExactly(
                Instant.parse("2026-03-10T14:00:00Z"), Instant.parse("2026-03-10T14:30:00Z"));
    }

    @Test
    void horizonteMaximoExcluyeFechasFueraDelRango() {
        Instant now = Instant.parse("2026-03-10T12:00:00Z");
        AvailabilityWindow window = window(BA, Clock.fixed(now, BA), Audience.PUBLIC, 0, 60);

        assertThat(window.isWithinHorizon(LocalDate.of(2026, 5, 8))).isTrue();
        assertThat(window.isWithinHorizon(LocalDate.of(2026, 5, 9))).isTrue();
        assertThat(window.isWithinHorizon(LocalDate.of(2026, 5, 10))).isFalse();
    }

    @Test
    void audienciaInternaNoAplicaAnticipacionNiHorizonte() {
        Instant now = Instant.parse("2026-03-10T12:00:00Z");
        AvailabilityWindow window = window(BA, Clock.fixed(now, BA), Audience.INTERNAL, 120, 60);
        Interval temprano = interval("2026-03-10T13:00:00Z", "2026-03-10T13:30:00Z");

        assertThat(window.filter(List.of(temprano))).containsExactly(temprano);
        assertThat(window.isWithinHorizon(LocalDate.of(2027, 3, 10))).isTrue();
        assertThat(window.isWithinHorizon(LocalDate.of(2026, 3, 10))).isTrue();
    }

    @Test
    void diaLocalDistintoDelUtcConHorizonteSeMideEnLaZonaDelConsultorio() {
        AvailabilityWindow window = window(BA,
                Clock.fixed(Instant.parse("2026-03-10T02:30:00Z"), BA), Audience.PUBLIC, 0, 60);

        assertThat(window.isWithinHorizon(LocalDate.of(2026, 3, 9))).isTrue();
        assertThat(window.isWithinHorizon(LocalDate.of(2026, 5, 8))).isTrue();
        assertThat(window.isWithinHorizon(LocalDate.of(2026, 5, 9))).isFalse();
    }

    @Test
    void saltoDeHorarioHaciaAtrasTambienDescartaLaHoraInexistente() {
        ZoneId newYork = ZoneId.of("America/New_York");
        AvailabilityWindow window = window(newYork,
                Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), newYork),
                Audience.PUBLIC, 0, 60);

        assertThat(window.instantOf(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30))).isPresent();
        assertThat(window.instantOf(LocalDate.of(2026, 11, 1), LocalTime.of(3, 30)))
                .contains(Instant.parse("2026-11-01T08:30:00Z"));
    }

    @Test
    void inicioExactoEnLaAnticipacionMinimaSeOfrece() {
        Instant now = Instant.parse("2026-03-10T12:00:00Z");
        AvailabilityWindow window = window(BA, Clock.fixed(now, BA), Audience.PUBLIC, 120, 60);
        Interval limite = interval("2026-03-10T14:00:00Z", "2026-03-10T14:30:00Z");

        assertThat(window.isBookable(limite.start())).isTrue();
        assertThat(window.isBookable(limite.start().minusNanos(1))).isFalse();
    }

    static AvailabilityWindow window(ZoneId zone, Clock clock, Audience audience,
            int minNoticeMin, int maxAdvanceDays) {
        BookingSettings settings = new BookingSettings(zone, Duration.ofMinutes(15),
                Duration.ofMinutes(minNoticeMin), maxAdvanceDays);
        return new AvailabilityWindow(clock, settings, audience);
    }

    static List<Instant> startsOf(List<Interval> slots) {
        return slots.stream().map(Interval::start).toList();
    }

    static Interval interval(String from, String to) {
        return new Interval(Instant.parse(from), Instant.parse(to));
    }
}
