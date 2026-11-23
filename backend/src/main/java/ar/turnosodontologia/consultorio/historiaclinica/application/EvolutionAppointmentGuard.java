package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Valida el turno que se asocia a una evolucion: debe existir, ser del mismo paciente, no estar cancelado ni haber
 * sido un ausente y haber comenzado ya (inicio menor o igual al instante del servidor). Un turno ajeno y uno
 * inexistente no se distinguen. Solo lee de agenda por su {@code api}.
 */
@Component
public class EvolutionAppointmentGuard {

    private final AppointmentNotificationLookup appointments;
    private final Clock clock;

    public EvolutionAppointmentGuard(AppointmentNotificationLookup appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    public void check(UUID patientId, UUID appointmentId) {
        AppointmentNotificationView view = appointments.find(appointmentId)
                .filter(found -> found.patientId().equals(patientId))
                .orElseThrow(() -> new BusinessRuleException("EVOLUTION_APPOINTMENT_NOT_FOUND"));
        if (view.status() == AppointmentStatusView.CANCELLED || view.status() == AppointmentStatusView.NO_SHOW) {
            throw new BusinessRuleException("EVOLUTION_APPOINTMENT_NOT_ATTENDED");
        }
        if (view.startsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("EVOLUTION_APPOINTMENT_NOT_STARTED");
        }
    }
}
