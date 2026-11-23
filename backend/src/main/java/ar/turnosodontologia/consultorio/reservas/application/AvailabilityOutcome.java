package ar.turnosodontologia.consultorio.reservas.application;

import ar.turnosodontologia.consultorio.reservas.domain.Interval;
import java.util.List;

/** Resultado del caso de uso, en tipos de dominio; la fachada lo traduce a `reservas.api`. */
public record AvailabilityOutcome(boolean noActiveServices, List<Interval> slots) {
}
