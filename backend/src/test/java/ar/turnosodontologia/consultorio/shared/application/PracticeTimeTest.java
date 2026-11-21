package ar.turnosodontologia.consultorio.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticeTimeTest {

    private static PracticeTime practiceTime(String instant, String zone) {
        PracticeRepository repository =
                () -> List.of(Practice.existing(UUID.randomUUID(), "Consultorio", zone));
        return new PracticeTime(
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), new InstallationPractice(repository));
    }

    @Test
    void despuesDeLaMedianochePorUtcLaFechaLocalSigueSiendoElDiaAnterior() {
        PracticeTime time = practiceTime("2026-03-11T02:30:00Z", "America/Argentina/Buenos_Aires");

        assertThat(time.now()).isEqualTo(Instant.parse("2026-03-11T02:30:00Z"));
        assertThat(time.today()).isEqualTo(LocalDate.parse("2026-03-10"));
    }

    @Test
    void alMedioDiaUtcLaFechaLocalEsElMismoDia() {
        PracticeTime time = practiceTime("2026-03-10T12:00:00Z", "America/Argentina/Buenos_Aires");

        assertThat(time.today()).isEqualTo(LocalDate.parse("2026-03-10"));
    }

    @Test
    void laFechaLocalDependeDeLaZonaDelConsultorio() {
        PracticeTime time = practiceTime("2026-03-10T23:30:00Z", "Pacific/Auckland");

        assertThat(time.today()).isEqualTo(LocalDate.parse("2026-03-11"));
    }

    @Test
    void conviertePorLaZonaDelConsultorio() {
        PracticeTime time = practiceTime("2026-03-10T12:00:00Z", "America/Argentina/Buenos_Aires");

        assertThat(time.toLocal(Instant.parse("2026-03-10T12:00:00Z")))
                .isEqualTo(LocalDateTime.parse("2026-03-10T09:00:00"));
        assertThat(time.toInstant(LocalDateTime.parse("2026-03-10T09:00:00")))
                .isEqualTo(Instant.parse("2026-03-10T12:00:00Z"));
        assertThat(time.dayBounds(LocalDate.parse("2026-03-10")).start())
                .isEqualTo(Instant.parse("2026-03-10T03:00:00Z"));
    }
}
