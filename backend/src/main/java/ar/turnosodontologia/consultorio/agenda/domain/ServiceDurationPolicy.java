package ar.turnosodontologia.consultorio.agenda.domain;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;

/**
 * Duración de una prestación: mayor que cero y múltiplo de la granularidad vigente de turnos (design D4).
 */
public final class ServiceDurationPolicy {

    private ServiceDurationPolicy() {
    }

    public static void validate(int durationMin, int granularityMin) {
        if (durationMin <= 0) {
            throw new ValidationException("duration_min", "duration_min debe ser mayor que 0");
        }
        if (granularityMin <= 0 || durationMin % granularityMin != 0) {
            throw new BusinessRuleException("DURATION_NOT_MULTIPLE_OF_GRANULARITY");
        }
    }
}
