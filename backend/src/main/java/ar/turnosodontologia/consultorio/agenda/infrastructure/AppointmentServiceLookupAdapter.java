package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentServiceLookup;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador de solo lectura del identificador de prestación de un turno. */
@Component
class AppointmentServiceLookupAdapter implements AppointmentServiceLookup {

    private final AppointmentRepository appointments;

    AppointmentServiceLookupAdapter(AppointmentRepository appointments) {
        this.appointments = appointments;
    }

    @Override
    public Optional<UUID> findServiceId(UUID appointmentId) {
        if (appointmentId == null) {
            return Optional.empty();
        }
        return appointments.findById(appointmentId).map(appointment -> appointment.serviceId());
    }
}
