package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.PublicService;
import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador delgado: lee el repositorio de prestaciones sin exponer la entidad JPA. */
@Component
public class ServiceAvailabilityLookupAdapter implements ServiceAvailabilityLookup {

    private final PracticeServiceRepository repository;
    private final InstallationPractice installationPractice;

    public ServiceAvailabilityLookupAdapter(
            PracticeServiceRepository repository,
            InstallationPractice installationPractice) {
        this.repository = repository;
        this.installationPractice = installationPractice;
    }

    @Override
    public Optional<PublicService> findPublicService(UUID practiceId, UUID serviceId) {
        requireInstallation(practiceId);
        return repository.findById(serviceId)
                .filter(service -> practiceId.equals(service.practiceId()))
                .map(this::toPublicService);
    }

    @Override
    public boolean hasPublicServices(UUID practiceId) {
        requireInstallation(practiceId);
        return !repository.findPublicByPracticeId(practiceId).isEmpty();
    }

    private PublicService toPublicService(PracticeService service) {
        return new PublicService(
                service.id(),
                Duration.ofMinutes(service.durationMin()),
                service.isActive(),
                service.isPublicVisible());
    }

    private void requireInstallation(UUID practiceId) {
        if (!installationPractice.id().equals(practiceId)) {
            throw new IllegalStateException("El consultorio solicitado no es el de la instalación");
        }
    }
}
