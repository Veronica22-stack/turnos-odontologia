package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentCancelled;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentRescheduled;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.notificaciones.application.DispatchTrigger;
import ar.turnosodontologia.consultorio.notificaciones.application.NotificationScheduler;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce los eventos de turno (publicados dentro de la transaccion de la agenda) en notificaciones encoladas
 * (design D3). Escucha en {@code AFTER_COMMIT}: nunca ve un turno revertido y, como el encolado corre en una
 * transaccion nueva, un error aca no puede revertirlo. Todo error se loguea solo con identificadores y NO se relanza:
 * en {@code AFTER_COMMIT} una excepcion llegaria a quien hizo el commit y la API respondería 500 con el turno ya
 * guardado (RN-NO-05).
 */
@Component
class AppointmentEventsListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppointmentEventsListener.class);

    private final NotificationScheduler scheduler;
    private final DispatchTrigger dispatchTrigger;

    AppointmentEventsListener(NotificationScheduler scheduler, DispatchTrigger dispatchTrigger) {
        this.scheduler = scheduler;
        this.dispatchTrigger = dispatchTrigger;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AppointmentConfirmed event) {
        handle("AppointmentConfirmed", event.appointmentId(),
                () -> scheduler.onConfirmed(event.practiceId(), event.appointmentId(), event.startsAt()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AppointmentRescheduled event) {
        handle("AppointmentRescheduled", event.appointmentId(),
                () -> scheduler.onRescheduled(event.practiceId(), event.appointmentId(), event.startsAt()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(AppointmentCancelled event) {
        handle("AppointmentCancelled", event.appointmentId(), () -> scheduler.onCancelled(event.practiceId(),
                event.appointmentId(), event.cancelledBy() == CancelledByView.PATIENT));
    }

    private void handle(String eventName, UUID appointmentId, Runnable enqueue) {
        try {
            enqueue.run();
            dispatchTrigger.requestDispatch();
        } catch (RuntimeException error) {
            // Solo la clase de la excepcion: su mensaje puede traer datos personales (p. ej. un email).
            LOG.warn("Error al encolar notificaciones event={} appointmentId={} error={}", eventName, appointmentId,
                    error.getClass().getSimpleName());
        }
    }
}
