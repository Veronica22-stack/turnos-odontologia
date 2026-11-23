package ar.turnosodontologia.consultorio.reservas.application;

import ar.turnosodontologia.consultorio.reservas.domain.Audience;
import java.time.LocalDate;
import java.util.UUID;

/** Consulta interna del caso de uso; `application` no depende de `reservas.api`. */
public record AvailabilityQuery(UUID practiceId, UUID serviceId, LocalDate date, Audience audience) {

    public AvailabilityQuery withDate(LocalDate newDate) {
        return new AvailabilityQuery(practiceId, serviceId, newDate, audience);
    }
}
