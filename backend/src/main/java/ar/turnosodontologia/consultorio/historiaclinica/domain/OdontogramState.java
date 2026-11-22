package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Estado del odontograma derivado de sus entradas: para cada posicion (pieza completa o cara), la entrada mas
 * reciente que no fue corregida, mas el historial completo. Funcion pura, sin acceso a datos.
 */
public record OdontogramState(List<OdontogramEntry> current, List<Item> history) {

    /** Entrada del historial con el id de la entrada que la corrigio, si existe. */
    public record Item(OdontogramEntry entry, UUID correctedByEntryId) {
    }

    private static final Comparator<OdontogramEntry> NEWEST_FIRST = Comparator
            .comparing(OdontogramEntry::createdAt, Comparator.reverseOrder())
            .thenComparing(OdontogramEntry::id, Comparator.reverseOrder());

    private static final Comparator<OdontogramEntry> BY_POSITION = Comparator
            .comparing((OdontogramEntry entry) -> entry.position().tooth().value())
            .thenComparing(entry -> entry.position().surface(), Comparator.nullsFirst(Comparator.naturalOrder()));

    public OdontogramState {
        current = List.copyOf(current);
        history = List.copyOf(history);
    }

    public static OdontogramState derive(List<OdontogramEntry> entries) {
        List<OdontogramEntry> newestFirst = new ArrayList<>(entries);
        newestFirst.sort(NEWEST_FIRST);

        Map<UUID, UUID> correctedBy = new HashMap<>();
        for (OdontogramEntry entry : newestFirst) {
            if (entry.correctsEntryId() != null) {
                correctedBy.put(entry.correctsEntryId(), entry.id());
            }
        }

        Map<ToothPosition, OdontogramEntry> latest = new LinkedHashMap<>();
        Set<UUID> corrected = new HashSet<>(correctedBy.keySet());
        for (OdontogramEntry entry : newestFirst) {
            if (!corrected.contains(entry.id())) {
                latest.putIfAbsent(entry.position(), entry);
            }
        }

        List<OdontogramEntry> current = new ArrayList<>(latest.values());
        current.sort(BY_POSITION);
        List<Item> history = newestFirst.stream()
                .map(entry -> new Item(entry, correctedBy.get(entry.id())))
                .toList();
        return new OdontogramState(current, history);
    }
}
