package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentCancelled;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentRescheduled;
import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentEventsPublisher;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Mapea los tipos de dominio a los eventos publicos de {@code agenda.api} y los publica dentro de la transaccion en
 * curso (design D11). Los consumidores deben escuchar con {@code @TransactionalEventListener(AFTER_COMMIT)}.
 */
@Component
class SpringAppointmentEventsPublisher implements AppointmentEventsPublisher {

    private final ApplicationEventPublisher publisher;

    SpringAppointmentEventsPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void confirmed(Appointment appointment) {
        publisher.publishEvent(new AppointmentConfirmed(appointment.id(), appointment.practiceId(),
                appointment.patientId(), appointment.serviceId(), appointment.startsAt(), appointment.endsAt(),
                AppointmentMapping.origin(appointment.origin())));
    }

    @Override
    public void cancelled(Appointment appointment) {
        publisher.publishEvent(new AppointmentCancelled(appointment.id(), appointment.practiceId(),
                appointment.patientId(), appointment.startsAt(), appointment.endsAt(),
                AppointmentMapping.cancelledBy(appointment.cancelledBy())));
    }

    @Override
    public void rescheduled(Appointment appointment, Instant previousStartsAt, Instant previousEndsAt) {
        publisher.publishEvent(new AppointmentRescheduled(appointment.id(), appointment.practiceId(),
                appointment.patientId(), previousStartsAt, previousEndsAt, appointment.startsAt(),
                appointment.endsAt()));
    }
}
