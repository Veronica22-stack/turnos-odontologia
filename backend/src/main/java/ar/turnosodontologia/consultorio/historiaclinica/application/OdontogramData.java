package ar.turnosodontologia.consultorio.historiaclinica.application;

import java.util.List;

/** Estado actual derivado (por pieza y cara) e historial completo en orden descendente. */
public record OdontogramData(List<OdontogramEntryData> current, List<OdontogramEntryData> history) {

    static OdontogramData empty() {
        return new OdontogramData(List.of(), List.of());
    }
}
