package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Version de anamnesis: informa la entrada que corrige y su motivo (si es una correccion) y la entrada que la corrige
 * (si fue corregida).
 */
public record AnamnesisEntryResponse(
        UUID id,
        int version,
        AnamnesisContentResponse content,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    /** El motivo y el contenido son datos clinicos: solo identificadores (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisEntryResponse[id=" + id + ", version=" + version + "]";
    }
}
