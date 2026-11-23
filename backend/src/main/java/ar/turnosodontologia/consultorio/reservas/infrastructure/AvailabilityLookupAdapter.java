package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityStatus;
import ar.turnosodontologia.consultorio.reservas.api.AvailableSlot;
import ar.turnosodontologia.consultorio.reservas.application.AvailabilityOutcome;
import ar.turnosodontologia.consultorio.reservas.application.AvailabilityUseCase;
import ar.turnosodontologia.consultorio.reservas.domain.Audience;
import org.springframework.stereotype.Component;

/** Fachada delgada: traduce los records públicos al caso de uso y viceversa. */
@Component
public class AvailabilityLookupAdapter implements AvailabilityLookup {

    private final AvailabilityUseCase useCase;

    public AvailabilityLookupAdapter(AvailabilityUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public AvailabilityResult availability(AvailabilityQuery query) {
        Audience audience = query.audience() == ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience.INTERNAL
                ? Audience.INTERNAL : Audience.PUBLIC;
        AvailabilityOutcome outcome = useCase.findAvailability(new ar.turnosodontologia.consultorio.reservas.application.AvailabilityQuery(
                query.practiceId(), query.serviceId(), query.date(), audience));
        AvailabilityStatus status = outcome.noActiveServices()
                ? AvailabilityStatus.NO_ACTIVE_SERVICES : AvailabilityStatus.OK;
        return new AvailabilityResult(status, outcome.slots().stream()
                .map(slot -> new AvailableSlot(slot.start(), slot.end()))
                .toList());
    }
}
