package ar.turnosodontologia.consultorio.pacientes.infrastructure;

import ar.turnosodontologia.consultorio.pacientes.api.CreatePatientRequest;
import ar.turnosodontologia.consultorio.pacientes.api.PatientResponse;
import ar.turnosodontologia.consultorio.pacientes.api.PatientSummaryResponse;
import ar.turnosodontologia.consultorio.pacientes.api.UpdatePatientRequest;
import ar.turnosodontologia.consultorio.pacientes.application.PatientData;
import ar.turnosodontologia.consultorio.pacientes.application.PatientSummaryData;
import ar.turnosodontologia.consultorio.pacientes.application.PatientsService;
import ar.turnosodontologia.consultorio.shared.domain.ForbiddenException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de fichas de pacientes (US-050); DTOs de entrada/salida, nunca entidades JPA. */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientsController {

    private final PatientsService patientsService;

    public PatientsController(PatientsService patientsService) {
        this.patientsService = patientsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<List<PatientSummaryResponse>> search(@RequestParam(name = "q", required = false) String q) {
        List<PatientSummaryData> found = patientsService.searchPatients(q, true);
        return ResponseEntity.ok(found.stream()
                .map(p -> new PatientSummaryResponse(
                        p.id(), p.firstName(), p.lastName(), p.dni(), p.phone(), p.active()))
                .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<PatientResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(patientsService.getPatientById(id, currentUserId())));
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
        PatientData created = patientsService.createPatient(
                request.firstName(),
                request.lastName(),
                request.dni(),
                request.phone(),
                request.email(),
                parseBirthDate(request.birthDate()),
                request.address());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<PatientResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
        PatientData updated = patientsService.updatePatient(
                id,
                currentUserId(),
                request.firstName(),
                request.lastName(),
                request.phone(),
                request.email(),
                parseBirthDate(request.birthDate()),
                request.address());
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DENTIST')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        patientsService.deactivatePatient(id);
        return ResponseEntity.noContent().build();
    }

    private static PatientResponse toResponse(PatientData data) {
        return new PatientResponse(
                data.id(),
                data.firstName(),
                data.lastName(),
                data.dni(),
                data.phone(),
                data.email(),
                data.birthDate(),
                data.address(),
                data.source(),
                data.active(),
                data.createdAt(),
                data.updatedAt());
    }

    private static LocalDate parseBirthDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidationException("birth_date", "birth_date debe tener formato ISO yyyy-MM-dd");
        }
    }

    /** El nombre del principal de sesión es el id opaco de la cuenta (nunca el email). */
    private static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String name = authentication != null ? authentication.getName() : null;
        if (name == null) {
            throw new ForbiddenException("FORBIDDEN");
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("FORBIDDEN");
        }
    }
}
