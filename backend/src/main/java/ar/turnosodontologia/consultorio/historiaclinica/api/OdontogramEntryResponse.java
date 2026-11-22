package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Entrada del odontograma: informa la entrada que corrige y su motivo (si es una correccion) y la entrada que la
 * corrigio (si fue corregida).
 */
public record OdontogramEntryResponse(
        UUID id,
        String toothCode,
        String surface,
        String condition,
        String notes,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    /** Pieza, estado, notas y motivo son datos clinicos: solo el identificador (regla dura 6). */
    @Override
    public String toString() {
        return "OdontogramEntryResponse[id=" + id + "]";
    }
}
