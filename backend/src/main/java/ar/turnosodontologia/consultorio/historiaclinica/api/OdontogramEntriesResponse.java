package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.util.List;

/** Entradas creadas por un registro por lote, en el orden enviado. */
public record OdontogramEntriesResponse(List<OdontogramEntryResponse> entries) {
}
