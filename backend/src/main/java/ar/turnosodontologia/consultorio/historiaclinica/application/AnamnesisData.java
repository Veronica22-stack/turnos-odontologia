package ar.turnosodontologia.consultorio.historiaclinica.application;

import java.util.List;

/** Anamnesis vigente (nula si no hay) e historial completo en orden de version descendente. */
public record AnamnesisData(AnamnesisEntryData current, List<AnamnesisEntryData> history) {
}
