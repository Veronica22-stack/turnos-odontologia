package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Correccion de una evolucion: motivo y nueva descripcion. No lleva turno: el vinculo de la original se conserva (si
 * el cliente lo envia, se ignora).
 */
public record EvolutionCorrectionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Size(max = 5000) String description) {

    /** Motivo y descripcion son datos clinicos: nunca se imprimen (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionCorrectionRequest[***]";
    }
}
