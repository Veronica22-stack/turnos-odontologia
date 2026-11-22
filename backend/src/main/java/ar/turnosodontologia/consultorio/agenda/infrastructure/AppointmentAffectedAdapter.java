package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentSlot;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentsProvider;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto de dominio {@link AffectedAppointmentsProvider} (design D12) sobre el repositorio de turnos:
 * turnos {@code CONFIRMED} del consultorio que intersectan {@code [from, to)}, ordenados por inicio. Al existir este
 * bean, el {@code Optional} de {@code ScheduleBlockService} deja de estar vacio.
 */
@Component
class AppointmentAffectedAdapter implements AffectedAppointmentsProvider {

    private final AppointmentRepository repository;

    AppointmentAffectedAdapter(AppointmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<AffectedAppointmentSlot> findWithin(UUID practiceId, Instant from, Instant to) {
        return repository.findConfirmedWithin(practiceId, from, to).stream()
                .map(a -> new AffectedAppointmentSlot(a.id(), a.startsAt(), a.endsAt()))
                .toList();
    }
}
