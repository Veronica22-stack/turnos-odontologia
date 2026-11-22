package ar.turnosodontologia.consultorio.historiaclinica.api;

import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Estado de una pieza (o de una cara de pieza) a registrar. Estado y cara llegan como texto con el catalogo validado
 * por {@code @Pattern} para que un valor invalido produzca {@code 400 VALIDATION_FAILED} con el campo y sin eco del
 * valor. No hay id, autor ni fecha: el servidor los fija. Los campos desconocidos se ignoran.
 */
@SurfaceAllowedForCondition
public record OdontogramEntryRequest(
        @NotNull @Pattern(regexp = ToothCode.PATTERN) String toothCode,
        @Pattern(regexp = "^(OCCLUSAL|MESIAL|DISTAL|VESTIBULAR|LINGUAL)$") String surface,
        @NotNull @Pattern(regexp = "^(HEALTHY|CARIES|FILLED|MISSING|CROWN|ROOT_CANAL)$") String condition,
        @Size(max = 500) String notes) {

    /** Pieza, estado y notas son datos clinicos: nunca se imprimen (regla dura 6). */
    @Override
    public String toString() {
        return "OdontogramEntryRequest[***]";
    }
}
