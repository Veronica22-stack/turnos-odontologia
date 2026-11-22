package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.BlockedRanges;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class BlockedRangesIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID OTHER_PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000099");
    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");
    private static final Instant FOURTEEN = Instant.parse("2026-12-10T14:00:00Z");
    private static final Instant SIXTEEN = Instant.parse("2026-12-10T16:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);

    private final ScheduleBlockRepository repository;
    private final BlockedRanges blockedRanges;

    BlockedRangesIT(ScheduleBlockRepository repository, BlockedRanges blockedRanges) {
        this.repository = repository;
        this.blockedRanges = blockedRanges;
    }

    @BeforeEach
    void seed() {
        repository.save(ScheduleBlock.create(PRACTICE_ID, NINE, ELEVEN, "Vacaciones", CLOCK));
        repository.save(ScheduleBlock.create(PRACTICE_ID, FOURTEEN, SIXTEEN, "Trámite", CLOCK));
    }

    @Test
    void devuelveBloqueoParcialmenteSolapado() {
        assertThat(blockedRanges.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T10:30:00Z"),
                Instant.parse("2026-12-10T12:00:00Z")))
                .singleElement()
                .satisfies(interval -> {
                    assertThat(interval.startsAt()).isEqualTo(NINE);
                    assertThat(interval.endsAt()).isEqualTo(ELEVEN);
                    assertThat(interval.blockId()).isNotNull();
                });
    }

    @Test
    void devuelveBloqueoQueContieneElRangoConsultado() {
        assertThat(blockedRanges.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T09:30:00Z"),
                Instant.parse("2026-12-10T10:00:00Z")))
                .singleElement()
                .satisfies(interval -> {
                    assertThat(interval.startsAt()).isEqualTo(NINE);
                    assertThat(interval.endsAt()).isEqualTo(ELEVEN);
                });
    }

    @Test
    void noDevuelveBloqueoAdyacente() {
        assertThat(blockedRanges.findOverlapping(PRACTICE_ID, ELEVEN, Instant.parse("2026-12-10T11:30:00Z")))
                .isEmpty();
    }

    @Test
    void noDevuelveBloqueosEnRangoLibre() {
        assertThat(blockedRanges.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T12:00:00Z"), FOURTEEN))
                .isEmpty();
    }

    @Test
    void noDevuelveBloqueosDeOtroConsultorio() {
        assertThat(blockedRanges.findOverlapping(OTHER_PRACTICE_ID, NINE, ELEVEN)).isEmpty();
    }

    @Test
    void rechazaRangoInvertido() {
        assertThatThrownBy(() -> blockedRanges.findOverlapping(PRACTICE_ID, ELEVEN, NINE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
