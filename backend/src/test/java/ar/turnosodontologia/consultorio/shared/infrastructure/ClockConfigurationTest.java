package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ClockConfigurationTest {

    @Test
    void elRelojPublicadoEstaEnUtc() {
        Clock clock = new ClockConfiguration().clock();

        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void elRelojPublicadoEsElDelSistemaYAvanza() {
        Clock clock = new ClockConfiguration().clock();

        assertThat(clock.instant()).isBetween(java.time.Instant.now().minusSeconds(5), java.time.Instant.now().plusSeconds(5));
    }
}
