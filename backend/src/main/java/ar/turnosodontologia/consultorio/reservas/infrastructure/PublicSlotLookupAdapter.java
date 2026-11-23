package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.application.PublicSlotLookup;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adapta el contrato estable de disponibilidad de C-12 al puerto del caso de uso. */
@Component
class PublicSlotLookupAdapter implements PublicSlotLookup {

    private final AvailabilityLookup availability;

    PublicSlotLookupAdapter(AvailabilityLookup availability) {
        this.availability = availability;
    }

    @Override
    public boolean isOffered(UUID practiceId, UUID serviceId, LocalDate date, Instant startsAt) {
        return availability.availability(new AvailabilityQuery(
                practiceId, serviceId, date, AvailabilityAudience.PUBLIC)).slots().stream()
                .anyMatch(slot -> slot.startsAt().equals(startsAt));
    }
}
