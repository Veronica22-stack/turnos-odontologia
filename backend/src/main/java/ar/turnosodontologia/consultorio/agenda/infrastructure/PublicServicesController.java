package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.PublicServiceResponse;
import ar.turnosodontologia.consultorio.agenda.application.ServiceCatalogService;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import ar.turnosodontologia.consultorio.shared.infrastructure.PublicReadRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catálogo público de prestaciones (design D6/D7): solo GET, lista activas y visibles con DTO mínimo,
 * cabecera de cache corto y rate limit por IP; la clave del limitador nunca se loguea ni se devuelve.
 */
@RestController
@RequestMapping("/api/v1/public/services")
public class PublicServicesController {

    private static final String CACHE_CONTROL = "public, max-age=60";

    private final ServiceCatalogService catalog;
    private final PublicReadRateLimiter rateLimiter;
    private final Clock clock;

    public PublicServicesController(
            ServiceCatalogService catalog, PublicReadRateLimiter rateLimiter, Clock clock) {
        this.catalog = catalog;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<List<PublicServiceResponse>> listServices(HttpServletRequest request) {
        rejectIfRateLimited(clientIp(request));
        List<PublicServiceResponse> services = catalog.listPublicServices().stream()
                .map(PublicServiceResponse::from)
                .toList();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, CACHE_CONTROL)
                .body(services);
    }

    /** Helper central: obtención de la IP del request y armado del 429. */
    private void rejectIfRateLimited(String clientIp) {
        Optional<Instant> retryAt = rateLimiter.tryAcquire(clientIp, clock);
        retryAt.ifPresent(instant -> {
            throw new RateLimitedException(RateLimiter.retryAfterSeconds(clock.instant(), instant));
        });
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "";
    }
}
