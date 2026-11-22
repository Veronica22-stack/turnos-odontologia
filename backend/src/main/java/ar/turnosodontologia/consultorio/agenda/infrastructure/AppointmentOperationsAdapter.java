package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Delega en {@link AppointmentService} (design D16): mismo camino de escritura que usa el controller. */
@Component
class AppointmentOperationsAdapter implements AppointmentOperations {

    private final AppointmentService service;

    AppointmentOperationsAdapter(AppointmentService service) {
        this.service = service;
    }

    @Override
    public AppointmentView reschedule(UUID appointmentId, Instant newStartsAt, boolean overbooking) {
        // Contrato publico: un argumento ausente es un error de validacion (400), nunca un NullPointerException.
        if (appointmentId == null) {
            throw new ValidationException("appointmentId", "REQUIRED");
        }
        if (newStartsAt == null) {
            throw new ValidationException("newStartsAt", "REQUIRED");
        }
        AppointmentResult result = service.reschedule(appointmentId, newStartsAt, overbooking);
        return new AppointmentView(result.id(), result.patientId(), result.serviceId(), result.startsAt(),
                result.endsAt(), result.durationMin(), AppointmentMapping.status(result.status()),
                AppointmentMapping.origin(result.origin()), result.overbooking());
    }
}
