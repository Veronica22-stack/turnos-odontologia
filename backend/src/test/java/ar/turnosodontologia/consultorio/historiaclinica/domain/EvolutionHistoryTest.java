package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EvolutionHistoryTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-05-01T10:00:00Z");

    @Test
    void ordenaDeLaMasRecienteALaMasAntiguaSinDependerDelOrdenDeEntrada() {
        EvolutionEntry first = entry(1, T0, null);
        EvolutionEntry second = entry(2, T0.plusSeconds(60), null);
        EvolutionEntry third = entry(3, T0.plusSeconds(120), null);

        List<EvolutionHistory.Item> history = EvolutionHistory.of(List.of(second, third, first));

        assertThat(history).extracting(item -> item.entry().id())
                .containsExactly(third.id(), second.id(), first.id());
    }

    @Test
    void conIgualInstanteDesempataPorIdDescendenteYEsDeterminista() {
        EvolutionEntry low = entry(10, T0, null);
        EvolutionEntry mid = entry(20, T0, null);
        EvolutionEntry high = entry(30, T0, null);

        List<UUID> forward = EvolutionHistory.of(List.of(low, mid, high)).stream()
                .map(item -> item.entry().id()).toList();
        List<UUID> backward = EvolutionHistory.of(List.of(high, mid, low)).stream()
                .map(item -> item.entry().id()).toList();

        assertThat(forward).containsExactly(high.id(), mid.id(), low.id());
        assertThat(backward).isEqualTo(forward);
    }

    @Test
    void marcaLaEntradaCorregidaConElIdDeSuCorreccionYDejaNulaLaVigente() {
        EvolutionEntry original = entry(1, T0, null);
        EvolutionEntry correction = entry(2, T0.plusSeconds(60), original.id());
        EvolutionEntry untouched = entry(3, T0.plusSeconds(30), null);

        List<EvolutionHistory.Item> history = EvolutionHistory.of(List.of(original, correction, untouched));

        assertThat(history).extracting(item -> item.entry().id())
                .containsExactly(correction.id(), untouched.id(), original.id());
        assertThat(history).extracting(EvolutionHistory.Item::correctedByEntryId)
                .containsExactly(null, null, correction.id());
    }

    @Test
    void resuelveUnaCadenaDeCorreccionesEncadenadas() {
        EvolutionEntry a = entry(1, T0, null);
        EvolutionEntry b = entry(2, T0.plusSeconds(60), a.id());
        EvolutionEntry c = entry(3, T0.plusSeconds(120), b.id());

        List<EvolutionHistory.Item> history = EvolutionHistory.of(List.of(b, a, c));

        assertThat(history).extracting(item -> item.entry().id()).containsExactly(c.id(), b.id(), a.id());
        assertThat(history).extracting(EvolutionHistory.Item::correctedByEntryId)
                .containsExactly(null, c.id(), b.id());
    }

    @Test
    void unaListaVaciaDaUnHistorialVacio() {
        assertThat(EvolutionHistory.of(List.of())).isEmpty();
    }

    private static EvolutionEntry entry(long sequence, Instant createdAt, UUID corrects) {
        EvolutionEntry entry = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("evolucion " + sequence), null,
                new EntryProvenance(AUTHOR_ID, createdAt));
        if (corrects != null) {
            ReflectionTestUtils.setField(entry, "correctsEntryId", corrects);
            ReflectionTestUtils.setField(entry, "correctionReason", "motivo");
        }
        ReflectionTestUtils.setField(entry, "id", new UUID(0L, sequence));
        return entry;
    }
}
