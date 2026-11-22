package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.BlockedInterval;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlockedRangesTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID BLOCK_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant TEN_THIRTY = Instant.parse("2026-12-10T10:30:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-12-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository repository;

    @InjectMocks
    private ScheduleBlockBlockedRangesAdapter adapter;

    @Test
    void devuelveBloqueoQueSolapaParcialmente() {
        when(repository.findOverlapping(PRACTICE_ID, TEN_THIRTY, TWELVE))
                .thenReturn(List.of(block(NINE, ELEVEN)));

        assertThat(adapter.findOverlapping(PRACTICE_ID, TEN_THIRTY, TWELVE))
                .containsExactly(new BlockedInterval(BLOCK_ID, NINE, ELEVEN));
    }

    @Test
    void devuelveBloqueoQueContieneElRangoConsultado() {
        when(repository.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T09:30:00Z"),
                Instant.parse("2026-12-10T10:00:00Z")))
                .thenReturn(List.of(block(NINE, ELEVEN)));

        assertThat(adapter.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T09:30:00Z"),
                Instant.parse("2026-12-10T10:00:00Z")))
                .containsExactly(new BlockedInterval(BLOCK_ID, NINE, ELEVEN));
    }

    @Test
    void ignoraBloqueoAdyacenteAunqueElRepositorioLoDevuelva() {
        when(repository.findOverlapping(PRACTICE_ID, ELEVEN, TWELVE))
                .thenReturn(List.of(block(NINE, ELEVEN)));

        assertThat(adapter.findOverlapping(PRACTICE_ID, ELEVEN, TWELVE)).isEmpty();
    }

    @Test
    void devuelveListaVaciaSinBloqueos() {
        when(repository.findOverlapping(PRACTICE_ID, NINE, TWELVE)).thenReturn(List.of());

        assertThat(adapter.findOverlapping(PRACTICE_ID, NINE, TWELVE)).isEmpty();
    }

    @Test
    void rechazaRangoInvertidoONuloSinConsultarRepositorio() {
        assertThatThrownBy(() -> adapter.findOverlapping(PRACTICE_ID, TWELVE, NINE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter.findOverlapping(PRACTICE_ID, NINE, NINE))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    private static ScheduleBlock block(Instant startsAt, Instant endsAt) {
        return ScheduleBlock.existing(BLOCK_ID, PRACTICE_ID, startsAt, endsAt, "Vacaciones",
                CLOCK.instant(), CLOCK.instant());
    }
}
