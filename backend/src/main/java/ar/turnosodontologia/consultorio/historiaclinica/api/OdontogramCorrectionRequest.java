package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Correccion de una entrada del odontograma: motivo, nuevo estado y notas. No lleva pieza ni cara: la posicion de la
 * original no se corrige (si el cliente las envia, se ignoran).
 */
public record OdontogramCorrectionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Pattern(regexp = "^(HEALTHY|CARIES|FILLED|MISSING|CROWN|ROOT_CANAL)$") String condition,
        @Size(max = 500) String notes) {

    /** Motivo, estado y notas son datos clinicos: nunca se imprimen (regla dura 6). */
    @Override
    public String toString() {
        return "OdontogramCorrectionRequest[***]";
    }
}
