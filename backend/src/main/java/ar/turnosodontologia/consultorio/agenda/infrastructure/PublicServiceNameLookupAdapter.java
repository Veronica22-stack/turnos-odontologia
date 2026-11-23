package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.PublicServiceNameLookup;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Expone solo el nombre de la prestación para la confirmación de la reserva. */
@Component
class PublicServiceNameLookupAdapter implements PublicServiceNameLookup {

    private final PracticeServiceRepository repository;

    PublicServiceNameLookupAdapter(PracticeServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> findName(UUID practiceId, UUID serviceId) {
        return repository.findById(serviceId)
                .filter(service -> practiceId.equals(service.practiceId()))
                .map(ar.turnosodontologia.consultorio.agenda.domain.PracticeService::name);
    }
}
