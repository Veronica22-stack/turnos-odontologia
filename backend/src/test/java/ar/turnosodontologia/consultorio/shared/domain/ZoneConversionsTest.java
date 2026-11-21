package ar.turnosodontologia.consultorio.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ZoneConversionsTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void toLocalConvierteUtcAHoraDeBuenosAires() {
        assertThat(ZoneConversions.toLocal(Instant.parse("2026-03-10T12:00:00Z"), BUENOS_AIRES))
                .isEqualTo(LocalDateTime.parse("2026-03-10T09:00:00"));
    }

    @Test
    void toLocalRetrocedeElDiaCuandoUtcYaPasoLaMedianoche() {
        assertThat(ZoneConversions.toLocal(Instant.parse("2026-03-11T02:00:00Z"), BUENOS_AIRES))
                .isEqualTo(LocalDateTime.parse("2026-03-10T23:00:00"));
    }

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @Test
    void toInstantConvierteHoraLocalDeBuenosAiresAUtc() {
        assertThat(ZoneConversions.toInstant(LocalDateTime.parse("2026-03-10T09:00:00"), BUENOS_AIRES))
                .isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
    }

    @Test
    void toInstantRechazaUnaHoraInexistenteEnElSaltoDeHorario() {
        assertThatThrownBy(() -> ZoneConversions.toInstant(LocalDateTime.parse("2026-03-08T02:30:00"), NEW_YORK))
                .isInstanceOf(NonexistentLocalTimeException.class);
    }

    @Test
    void toInstantElijeElDesfaseMasTempranoEnUnaHoraAmbigua() {
        assertThat(ZoneConversions.toInstant(LocalDateTime.parse("2026-11-01T01:30:00"), NEW_YORK))
                .isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
    }

    @Test
    void dayBoundsDevuelveElDiaLocalComoRangoSemiabiertoEnUtc() {
        InstantRange range = ZoneConversions.dayBounds(LocalDate.parse("2026-03-10"), BUENOS_AIRES);

        assertThat(range.start()).isEqualTo(Instant.parse("2026-03-10T03:00:00Z"));
        assertThat(range.end()).isEqualTo(Instant.parse("2026-03-11T03:00:00Z"));
    }

    @Test
    void dayBoundsDeUnDiaConCambioDeHoraDura23Horas() {
        InstantRange range = ZoneConversions.dayBounds(LocalDate.parse("2026-03-08"), NEW_YORK);

        assertThat(range.start()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
        assertThat(range.end()).isEqualTo(Instant.parse("2026-03-09T04:00:00Z"));
        assertThat(Duration.between(range.start(), range.end())).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void instantRangeRechazaInicioNoAnteriorAlFin() {
        Instant instant = Instant.parse("2026-03-10T03:00:00Z");

        assertThatThrownBy(() -> new InstantRange(instant, instant)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstantRange(instant.plusSeconds(1), instant))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void instantRangeEsSemiabierto() {
        InstantRange range = new InstantRange(Instant.parse("2026-03-10T03:00:00Z"), Instant.parse("2026-03-11T03:00:00Z"));

        assertThat(range.contains(range.start())).isTrue();
        assertThat(range.contains(range.end())).isFalse();
    }
}
