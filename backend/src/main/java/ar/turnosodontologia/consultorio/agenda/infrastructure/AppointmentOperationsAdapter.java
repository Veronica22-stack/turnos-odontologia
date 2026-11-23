package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.agenda.api.PublicBookingCommand;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.application.BookAppointmentCommand;
import ar.turnosodontologia.consultorio.agenda.domain.CancelledBy;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Delega en {@link AppointmentService}: mismo camino de escritura que usa el controller (design D16, C-14/C-15). */
@Component
class AppointmentOperationsAdapter implements AppointmentOperations {

    private final AppointmentService service;

    AppointmentOperationsAdapter(AppointmentService service) {
        this.service = service;
    }

    @Override
    public AppointmentView book(PublicBookingCommand command) {
        if (command == null || command.patientId() == null || command.serviceId() == null || command.startsAt() == null) {
            throw new ValidationException("booking", "REQUIRED");
        }
        return toView(service.book(BookAppointmentCommand.publicBooking(
                command.patientId(), command.serviceId(), command.startsAt())));
    }

    @Override
    public AppointmentView reschedule(UUID appointmentId, Instant newStartsAt, boolean overbooking) {
        // Contrato público: un argumento ausente es un error de validación (400), nunca un NullPointerException.
        if (appointmentId == null) {
            throw new ValidationException("appointmentId", "REQUIRED");
        }
        if (newStartsAt == null) {
            throw new ValidationException("newStartsAt", "REQUIRED");
        }
        return toView(service.reschedule(appointmentId, newStartsAt, overbooking));
    }

    @Override
    public AppointmentView cancel(UUID appointmentId, CancelledByView cancelledBy) {
        if (appointmentId == null) {
            throw new ValidationException("appointmentId", "REQUIRED");
        }
        if (cancelledBy == null) {
            throw new ValidationException("cancelledBy", "REQUIRED");
        }
        return toView(service.cancel(appointmentId, CancelledBy.valueOf(cancelledBy.name())));
    }

    private static AppointmentView toView(AppointmentResult result) {
        return new AppointmentView(result.id(), result.patientId(), result.serviceId(), result.startsAt(),
                result.endsAt(), result.durationMin(), AppointmentMapping.status(result.status()),
                AppointmentMapping.origin(result.origin()), result.overbooking());
    }
}
