package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityStatus;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import ar.turnosodontologia.consultorio.shared.infrastructure.PublicReadRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Disponibilidad para reserva online anónima, con límite de tasa y sin exponer datos del consultorio (D7/D8/D10). */
@RestController
@RequestMapping("/api/v1/public/availability")
public class AvailabilityPublicController {

    private final AvailabilityLookup availability;
    private final ServiceAvailabilityLookup services;
    private final InstallationPractice installationPractice;
    private final PublicReadRateLimiter rateLimiter;
    private final Clock clock;

    public AvailabilityPublicController(
            AvailabilityLookup availability,
            ServiceAvailabilityLookup services,
            InstallationPractice installationPractice,
            PublicReadRateLimiter rateLimiter,
            Clock clock) {
        this.availability = availability;
        this.services = services;
        this.installationPractice = installationPractice;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @GetMapping
    public AvailabilityResult availability(
            HttpServletRequest request,
            @RequestParam(required = false) String serviceId,
            @RequestParam(required = false) String date) {
        rejectIfRateLimited(clientIp(request));
        AvailabilityParams.Parsed params = AvailabilityParams.parse(serviceId, date);
        UUID practiceId = installationPractice.id();
        if (!services.hasPublicServices(practiceId)) {
            return new AvailabilityResult(AvailabilityStatus.NO_ACTIVE_SERVICES, List.of());
        }
        return availability.availability(new AvailabilityQuery(
                practiceId, params.serviceId(), params.date(), AvailabilityAudience.PUBLIC));
    }

    private void rejectIfRateLimited(String clientIp) {
        Optional<Instant> retryAt = rateLimiter.tryAcquire(clientIp, clock);
        retryAt.ifPresent(instant -> {
            throw new RateLimitedException(RateLimiter.retryAfterSeconds(clock.instant(), instant));
        });
    }

    private static String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "";
    }
}
