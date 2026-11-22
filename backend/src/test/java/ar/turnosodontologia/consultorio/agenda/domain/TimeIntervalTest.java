package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeIntervalTest {

    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-12-10T12:00:00Z");

    @Test
    void rechazaRangoInvertido() {
        assertThatThrownBy(() -> new TimeInterval(ELEVEN, NINE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaDuracionNula() {
        assertThatThrownBy(() -> new TimeInterval(NINE, NINE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaInstantesNulos() {
        assertThatThrownBy(() -> new TimeInterval(null, ELEVEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeInterval(NINE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectaSolapamientoParcialEnCualquierOrden() {
        TimeInterval first = new TimeInterval(NINE, ELEVEN);
        TimeInterval second = new TimeInterval(Instant.parse("2026-12-10T10:00:00Z"), TWELVE);

        assertThat(first.intersects(second)).isTrue();
        assertThat(second.intersects(first)).isTrue();
    }

    @Test
    void detectaIntervaloContenido() {
        TimeInterval outer = new TimeInterval(NINE, ELEVEN);
        TimeInterval inner = new TimeInterval(Instant.parse("2026-12-10T09:30:00Z"),
                Instant.parse("2026-12-10T10:00:00Z"));

        assertThat(outer.intersects(inner)).isTrue();
        assertThat(inner.intersects(outer)).isTrue();
    }

    @Test
    void losIntervalosAdyacentesNoSeSolapan() {
        TimeInterval first = new TimeInterval(NINE, ELEVEN);
        TimeInterval adjacent = new TimeInterval(ELEVEN, TWELVE);

        assertThat(first.intersects(adjacent)).isFalse();
        assertThat(adjacent.intersects(first)).isFalse();
    }

    @Test
    void losIntervalosDisjuntosNoSeSolapan() {
        TimeInterval first = new TimeInterval(NINE, ELEVEN);
        TimeInterval later = new TimeInterval(TWELVE, Instant.parse("2026-12-10T13:00:00Z"));

        assertThat(first.intersects(later)).isFalse();
    }
}
