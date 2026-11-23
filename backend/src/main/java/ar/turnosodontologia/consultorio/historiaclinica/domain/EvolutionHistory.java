package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Historial de evoluciones derivado de sus entradas: de la mas reciente a la mas antigua (por instante y, ante
 * empate, por id descendente), con el id de la entrada que corrigio a cada una. Funcion pura, sin acceso a datos.
 */
public final class EvolutionHistory {

    /** Entrada con el id de la entrada que la corrigio, o {@code null} si esta vigente. */
    public record Item(EvolutionEntry entry, UUID correctedByEntryId) {
    }

    private static final Comparator<EvolutionEntry> NEWEST_FIRST = Comparator
            .comparing(EvolutionEntry::createdAt, Comparator.reverseOrder())
            .thenComparing(EvolutionEntry::id, Comparator.reverseOrder());

    private EvolutionHistory() {
    }

    public static List<Item> of(List<EvolutionEntry> entries) {
        List<EvolutionEntry> newestFirst = new ArrayList<>(entries);
        newestFirst.sort(NEWEST_FIRST);
        Map<UUID, UUID> correctedBy = new HashMap<>();
        for (EvolutionEntry entry : newestFirst) {
            if (entry.correctsEntryId() != null) {
                correctedBy.put(entry.correctsEntryId(), entry.id());
            }
        }
        return newestFirst.stream().map(entry -> new Item(entry, correctedBy.get(entry.id()))).toList();
    }
}
