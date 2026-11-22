package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Lote de 1 a 64 estados de piezas, sin posiciones (pieza y cara) repetidas. */
@DistinctToothPositions
public record OdontogramRequest(
        @NotNull @Size(min = 1, max = 64) List<@NotNull @Valid OdontogramEntryRequest> entries) {

    /** Contenido clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "OdontogramRequest[***]";
    }
}
