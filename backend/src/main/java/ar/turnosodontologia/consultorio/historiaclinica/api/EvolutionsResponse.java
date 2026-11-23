package ar.turnosodontologia.consultorio.historiaclinica.api;

import java.util.List;

/** Historial de evoluciones del paciente, de la mas reciente a la mas antigua. */
public record EvolutionsResponse(List<EvolutionEntryResponse> entries) {
}
