package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.ActiveBookingsLookup;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implementación del conteo de turnos futuros activos sobre la tabla que agenda posee. */
@Component
class ActiveBookingsLookupAdapter implements ActiveBookingsLookup {

    private final SpringDataAppointmentRepository appointments;

    ActiveBookingsLookupAdapter(SpringDataAppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public long countActiveFuture(UUID practiceId, List<UUID> patientIds, Instant from) {
        if (patientIds == null || patientIds.isEmpty()) {
            return 0;
        }
        return appointments.countActiveFuture(practiceId, patientIds, from);
    }
}
