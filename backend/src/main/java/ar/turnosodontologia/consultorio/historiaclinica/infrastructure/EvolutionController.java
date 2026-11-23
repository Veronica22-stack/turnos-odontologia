package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.api.EvolutionCorrectionRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.EvolutionEntryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.EvolutionRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.EvolutionsResponse;
import ar.turnosodontologia.consultorio.historiaclinica.application.EvolutionCommand;
import ar.turnosodontologia.consultorio.historiaclinica.application.EvolutionService;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evoluciones del paciente: solo GET y POST (la historia clinica es append-only). Autor, instante e id los fija el
 * servidor; el cuerpo no los acepta, y una correccion no puede cambiar el turno asociado.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/clinical-record/evolutions")
class EvolutionController {

    private final EvolutionService evolutionService;

    EvolutionController(EvolutionService evolutionService) {
        this.evolutionService = evolutionService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<EvolutionsResponse> list(@PathVariable UUID patientId, HttpServletRequest request) {
        EvolutionsResponse body = new EvolutionsResponse(evolutionService
                .read(patientId, ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request))
                .stream().map(ClinicalRequestSupport::toResponse).toList());
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body);
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<EvolutionEntryResponse> register(
            @PathVariable UUID patientId, @Valid @RequestBody EvolutionRequest body, HttpServletRequest request) {
        UUID appointmentId = body.appointmentId() == null ? null : UUID.fromString(body.appointmentId());
        EvolutionEntryResponse created = ClinicalRequestSupport.toResponse(evolutionService.register(
                patientId, ClinicalRequestSupport.currentUserId(),
                new EvolutionCommand(EvolutionDescription.of(body.description()), appointmentId),
                ClinicalRequestSupport.contextOf(request)));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PostMapping("/{entryId}/corrections")
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<EvolutionEntryResponse> correct(
            @PathVariable UUID patientId,
            @PathVariable UUID entryId,
            @Valid @RequestBody EvolutionCorrectionRequest body,
            HttpServletRequest request) {
        EvolutionEntryResponse created = ClinicalRequestSupport.toResponse(evolutionService.correct(
                patientId, entryId, body.reason(), EvolutionDescription.of(body.description()),
                ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request)));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }
}
