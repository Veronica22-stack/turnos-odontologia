package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import java.time.Instant;
import java.util.UUID;

/** Vista de una version de anamnesis para la capa de entrada; {@code correctedByEntryId} es nulo si es vigente. */
public record AnamnesisEntryData(
        UUID id,
        int version,
        AnamnesisContent content,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    static AnamnesisEntryData from(AnamnesisEntry entry, UUID correctedByEntryId) {
        return new AnamnesisEntryData(
                entry.id(), entry.version(), entry.content(), entry.authorId(), entry.createdAt(),
                entry.correctsEntryId(), entry.correctionReason(), correctedByEntryId);
    }

    /** El contenido y el motivo son datos clinicos: solo identificadores (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisEntryData[id=" + id + ", version=" + version + "]";
    }
}
