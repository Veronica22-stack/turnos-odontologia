package ar.turnosodontologia.consultorio.agenda.infrastructure;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentResponse;
import ar.turnosodontologia.consultorio.agenda.api.CreateAppointmentRequest;
import ar.turnosodontologia.consultorio.agenda.api.RescheduleAppointmentRequest;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.application.BookAppointmentCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** API interna de turnos del odontologo (design D17). No hay endpoint de borrado: los turnos solo cambian de estado. */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentsController {

    private final AppointmentService service;

    public AppointmentsController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResult result = service.book(new BookAppointmentCommand(request.patientId(), request.serviceId(),
                request.startsAt(), Boolean.TRUE.equals(request.overbooking()), request.notes()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.id())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(service.get(id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<AppointmentResponse>> list(
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        List<AppointmentResponse> appointments = service.list(parseInstant(from), parseInstant(to)).stream()
                .map(AppointmentsController::toResponse)
                .toList();
        return ResponseEntity.ok(appointments);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(service.cancel(id)));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(service.complete(id)));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> noShow(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(service.noShow(id)));
    }

    @PostMapping("/{id}/reschedule")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable UUID id, @Valid @RequestBody RescheduleAppointmentRequest request) {
        AppointmentResult result = service.reschedule(id, request.startsAt(),
                Boolean.TRUE.equals(request.overbooking()));
        return ResponseEntity.ok(toResponse(result));
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

    private static AppointmentResponse toResponse(AppointmentResult result) {
        return new AppointmentResponse(result.id(), result.patientId(), result.serviceId(), result.startsAt(),
                result.endsAt(), result.durationMin(), AppointmentMapping.status(result.status()),
                AppointmentMapping.origin(result.origin()), result.overbooking(),
                AppointmentMapping.cancelledBy(result.cancelledBy()), result.cancelledAt(), result.notes(),
                result.createdAt(), result.updatedAt());
    }
}
