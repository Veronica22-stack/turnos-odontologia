package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.CreateServiceRequest;
import ar.turnosodontologia.consultorio.agenda.api.ServiceResponse;
import ar.turnosodontologia.consultorio.agenda.api.UpdateServiceRequest;
import ar.turnosodontologia.consultorio.agenda.application.ServiceCatalogService;
import ar.turnosodontologia.consultorio.agenda.application.ServiceData;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de gestión del catálogo de prestaciones (design D5); DTOs de entrada/salida, nunca entidades JPA. */
@RestController
@RequestMapping("/api/v1/services")
public class ServicesController {

    private final ServiceCatalogService catalog;

    public ServicesController(ServiceCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<ServiceResponse>> listServices() {
        return ResponseEntity.ok(catalog.listServices().stream().map(ServiceResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ServiceResponse> getService(@PathVariable UUID id) {
        return ResponseEntity.ok(ServiceResponse.from(catalog.getService(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ServiceResponse> createService(@Valid @RequestBody CreateServiceRequest request) {
        ServiceData created = catalog.createService(
                request.name(), request.description(), request.durationMin(), booleanValue(request.publicVisible()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/services/" + created.id()))
                .body(ServiceResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ServiceResponse> updateService(
            @PathVariable UUID id, @Valid @RequestBody UpdateServiceRequest request) {
        ServiceData updated = catalog.updateService(
                id,
                request.name(),
                request.description(),
                request.durationMin(),
                booleanValue(request.publicVisible()),
                booleanValue(request.active()));
        return ResponseEntity.ok(ServiceResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<Void> deactivateService(@PathVariable UUID id) {
        catalog.deactivateService(id);
        return ResponseEntity.noContent().build();
    }

    private static boolean booleanValue(Boolean value) {
        return value != null && value;
    }
}
