package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adaptador de solo lectura del puerto {@link AppointmentNotificationLookup} (design D10): filtra por el consultorio
 * de la instalacion, resuelve el nombre vigente de la prestacion y nunca expone las notas del turno.
 */
@Component
class AppointmentNotificationLookupAdapter implements AppointmentNotificationLookup {

    private final AppointmentRepository appointments;
    private final PracticeServiceRepository services;
    private final InstallationPractice installationPractice;

    AppointmentNotificationLookupAdapter(AppointmentRepository appointments, PracticeServiceRepository services,
            InstallationPractice installationPractice) {
        this.appointments = appointments;
        this.services = services;
        this.installationPractice = installationPractice;
    }

    @Override
    public Optional<AppointmentNotificationView> find(UUID appointmentId) {
        UUID practiceId = installationPractice.id();
        return appointments.findById(appointmentId)
                .filter(appointment -> appointment.practiceId().equals(practiceId))
                .flatMap(appointment -> services.findById(appointment.serviceId())
                        .map(service -> new AppointmentNotificationView(appointment.id(), appointment.patientId(),
                                service.name(), appointment.startsAt(), appointment.endsAt(),
                                AppointmentMapping.status(appointment.status()))));
    }
}
