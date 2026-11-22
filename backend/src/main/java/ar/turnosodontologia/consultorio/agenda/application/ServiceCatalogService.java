package ar.turnosodontologia.consultorio.agenda.application;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.agenda.domain.ServiceDurationPolicy;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catalogo de prestaciones del consultorio (design D2/D3/D4/D5): alta con validación contra
 * la granularidad vigente, listado de gestión con inactivas, consulta por id, edición, baja lógica idempotente
 * y el catalogo público de solo lectura.
 */
@Service
public class ServiceCatalogService {

    private final PracticeServiceRepository practiceServiceRepository;
    private final PracticeRepository practiceRepository;
    private final InstallationPractice installationPractice;

    public ServiceCatalogService(
            PracticeServiceRepository practiceServiceRepository,
            PracticeRepository practiceRepository,
            InstallationPractice installationPractice) {
        this.practiceServiceRepository = practiceServiceRepository;
        this.practiceRepository = practiceRepository;
        this.installationPractice = installationPractice;
    }

    @Transactional(readOnly = true)
    public List<ServiceData> listServices() {
        UUID practiceId = installationPractice.id();
        return practiceServiceRepository.findAllByPracticeIdOrderByNameAsc(practiceId).stream()
                .map(ServiceData::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceData getService(UUID id) {
        return ServiceData.from(findService(id));
    }

    @Transactional
    public ServiceData createService(String name, String description, int durationMin, boolean publicVisible) {
        ServiceDurationPolicy.validate(durationMin, currentGranularityMin());
        PracticeService created = PracticeService.create(
                installationPractice.id(), name, description, durationMin, publicVisible);
        return ServiceData.from(practiceServiceRepository.save(created));
    }

    @Transactional
    public ServiceData updateService(
            UUID id, String name, String description, int durationMin, boolean publicVisible, boolean active) {
        PracticeService service = findService(id);
        ServiceDurationPolicy.validate(durationMin, currentGranularityMin());
        service.update(name, description, durationMin, publicVisible, active);
        return ServiceData.from(practiceServiceRepository.save(service));
    }

    @Transactional
    public void deactivateService(UUID id) {
        PracticeService service = findService(id);
        service.deactivate();
        practiceServiceRepository.save(service);
    }

    @Transactional(readOnly = true)
    public List<PublicServiceData> listPublicServices() {
        UUID practiceId = installationPractice.id();
        return practiceServiceRepository.findPublicByPracticeId(practiceId).stream()
                .map(PublicServiceData::from)
                .toList();
    }

    private PracticeService findService(UUID id) {
        UUID practiceId = installationPractice.id();
        return practiceServiceRepository.findById(id)
                .filter(service -> service.practiceId().equals(practiceId))
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND"));
    }

    /** Granularidad vigente: se lee cada vez porque la configuración cambia en caliente y no se cachea (design D4). */
    private int currentGranularityMin() {
        return practiceRepository.findById(installationPractice.id())
                .map(Practice::slotGranularityMin)
                .orElseThrow(() -> new IllegalStateException("Configuracion invalida: falta el consultorio de la instalacion"));
    }
}
