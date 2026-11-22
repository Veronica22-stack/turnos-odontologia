package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
import java.time.Instant;
import java.util.UUID;

/** Vista de una entrada del odontograma para la capa de entrada; {@code correctedByEntryId} es nulo si no fue corregida. */
public record OdontogramEntryData(
        UUID id,
        String toothCode,
        ToothSurface surface,
        ToothCondition condition,
        String notes,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    static OdontogramEntryData from(OdontogramEntry entry, UUID correctedByEntryId) {
        return new OdontogramEntryData(
                entry.id(), entry.position().tooth().value(), entry.position().surface(), entry.condition(),
                entry.notes(), entry.authorId(), entry.createdAt(), entry.correctsEntryId(),
                entry.correctionReason(), correctedByEntryId);
    }

    /** Pieza, estado, notas y motivo son datos clinicos: solo el identificador (regla dura 6). */
    @Override
    public String toString() {
        return "OdontogramEntryData[id=" + id + "]";
    }
}
