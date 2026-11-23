package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Evolucion clinica: informa el turno asociado (si lo tiene), la entrada que corrige y su motivo (si es una
 * correccion) y la entrada que la corrigio (si fue corregida).
 */
public record EvolutionEntryResponse(
        UUID id,
        String description,
        UUID appointmentId,
        UUID authorId,
        Instant createdAt,
        UUID correctsEntryId,
        String correctionReason,
        UUID correctedByEntryId) {

    /** Descripcion y motivo son datos clinicos: solo el identificador (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionEntryResponse[id=" + id + "]";
    }
}
