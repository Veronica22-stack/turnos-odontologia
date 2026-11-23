package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Disponibilidad para el odontólogo autenticado, sin filtros de anticipación (design D7). */
@RestController
@RequestMapping("/api/v1/availability")
public class AvailabilityInternalController {

    private final AvailabilityLookup availability;
    private final InstallationPractice installationPractice;

    public AvailabilityInternalController(AvailabilityLookup availability, InstallationPractice installationPractice) {
        this.availability = availability;
        this.installationPractice = installationPractice;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public AvailabilityResult availability(
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String date) {
        AvailabilityParams.Parsed params = AvailabilityParams.parse(serviceId, date);
        return availability.availability(new AvailabilityQuery(
                installationPractice.id(), params.serviceId(), params.date(), AvailabilityAudience.INTERNAL));
    }
}
