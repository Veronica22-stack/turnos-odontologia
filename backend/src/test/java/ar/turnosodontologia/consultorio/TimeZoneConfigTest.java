package ar.turnosodontologia.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimeZoneConfigTest {

    private TimeZone originalZone;

    @BeforeEach
    void rememberOriginalZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreOriginalZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void fijaLaZonaPorDefectoEnUtcPartiendoDeBuenosAires() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

        ConsultorioApplication.configureTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void esIdempotenteCuandoLaZonaYaEsUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ConsultorioApplication.configureTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void fijaLaZonaPorDefectoEnUtcPartiendoDeOtraZona() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));

        ConsultorioApplication.configureTimeZone();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
