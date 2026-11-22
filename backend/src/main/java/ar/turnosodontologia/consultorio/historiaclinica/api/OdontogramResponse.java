package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.util.List;

/** Estado actual por pieza y cara (ordenado por pieza FDI) e historial completo, del mas reciente al mas antiguo. */
public record OdontogramResponse(List<OdontogramEntryResponse> current, List<OdontogramEntryResponse> history) {
}
