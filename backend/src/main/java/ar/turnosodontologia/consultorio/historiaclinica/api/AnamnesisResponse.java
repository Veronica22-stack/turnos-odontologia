package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.util.List;

/** Anamnesis vigente (nula si no hay) e historial en orden de version descendente. */
public record AnamnesisResponse(AnamnesisEntryResponse current, List<AnamnesisEntryResponse> history) {
}
