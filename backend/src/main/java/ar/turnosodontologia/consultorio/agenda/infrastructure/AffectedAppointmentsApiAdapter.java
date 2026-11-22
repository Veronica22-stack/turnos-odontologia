package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentsProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Vista publica {@link AffectedAppointments} para otros modulos (design D12). Es un bean aparte del proveedor de
 * dominio porque ambas interfaces declaran {@code findWithin} con la misma firma y distinto tipo de retorno.
 */
@Component
class AffectedAppointmentsApiAdapter implements AffectedAppointments {

    private final AffectedAppointmentsProvider provider;

    AffectedAppointmentsApiAdapter(AffectedAppointmentsProvider provider) {
        this.provider = provider;
    }

    @Override
    public List<AffectedAppointment> findWithin(UUID practiceId, Instant from, Instant to) {
        return provider.findWithin(practiceId, from, to).stream()
                .map(slot -> new AffectedAppointment(slot.appointmentId(), slot.startsAt(), slot.endsAt()))
                .toList();
    }
}
