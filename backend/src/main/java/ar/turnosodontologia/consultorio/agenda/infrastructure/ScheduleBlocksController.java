package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.ScheduleBlockRequest;
import ar.turnosodontologia.consultorio.agenda.api.ScheduleBlockResponse;
import ar.turnosodontologia.consultorio.agenda.application.ScheduleBlockResult;
import ar.turnosodontologia.consultorio.agenda.application.ScheduleBlockService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/schedule-blocks")
public class ScheduleBlocksController {

    private final ScheduleBlockService service;

    public ScheduleBlocksController(ScheduleBlockService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<ScheduleBlockResponse>> list(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<ScheduleBlockResponse> blocks = service.list(parseInstant(from), parseInstant(to)).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(blocks);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ScheduleBlockResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(service.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ScheduleBlockResponse> create(@Valid @RequestBody ScheduleBlockRequest request) {
        ScheduleBlockResult result = service.create(request.startsAt(), request.endsAt(), request.reason());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.id())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(result));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<ScheduleBlockResponse> update(
            @PathVariable UUID id, @Valid @RequestBody ScheduleBlockRequest request) {
        ScheduleBlockResult result = service.update(id, request.startsAt(), request.endsAt(), request.reason());
        return ResponseEntity.ok(toResponse(result));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static Instant parseInstant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeException error) {
            throw new IllegalArgumentException("instante invalido");
        }
    }

    private ScheduleBlockResponse toResponse(ScheduleBlockResult result) {
        List<AffectedAppointment> affected = result.affectedAppointments().stream()
                .map(a -> new AffectedAppointment(a.appointmentId(), a.startsAt(), a.endsAt()))
                .toList();
        return new ScheduleBlockResponse(result.id(), result.startsAt(), result.endsAt(), result.reason(),
                result.createdAt(), result.updatedAt(), affected);
    }
}
