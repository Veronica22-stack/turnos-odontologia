package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import java.time.Instant;
import java.util.UUID;

/** Vista de una evolucion para la capa de entrada; {@code correctedByEntryId} es nulo si no fue corregida. */
public record EvolutionEntryData(
        UUID id,
        String description,
        UUID appointmentId,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    static EvolutionEntryData from(EvolutionEntry entry, UUID correctedByEntryId) {
        return new EvolutionEntryData(
                entry.id(), entry.description(), entry.appointmentId(), entry.authorId(), entry.createdAt(),
                entry.correctsEntryId(), entry.correctionReason(), correctedByEntryId);
    }

    /** Descripcion y motivo son datos clinicos: solo el identificador (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionEntryData[id=" + id + "]";
    }
}
