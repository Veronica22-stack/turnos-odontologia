package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisCorrectionRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisEntryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisResponse;
import ar.turnosodontologia.consultorio.historiaclinica.application.AnamnesisData;
import ar.turnosodontologia.consultorio.historiaclinica.application.AnamnesisService;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
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
 * Anamnesis del paciente: solo GET y POST (la historia clinica es append-only). Autor, instante, version e id los fija
 * el servidor; el cuerpo no los acepta.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/clinical-record/anamnesis")
class AnamnesisController {

    private final AnamnesisService anamnesisService;

    AnamnesisController(AnamnesisService anamnesisService) {
        this.anamnesisService = anamnesisService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<AnamnesisResponse> get(@PathVariable UUID patientId, HttpServletRequest request) {
        AnamnesisData data = anamnesisService.read(
                patientId, ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(new AnamnesisResponse(
                        ClinicalRequestSupport.toResponse(data.current()),
                        data.history().stream().map(ClinicalRequestSupport::toResponse).toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<AnamnesisEntryResponse> register(
            @PathVariable UUID patientId, @Valid @RequestBody AnamnesisRequest body, HttpServletRequest request) {
        AnamnesisEntryResponse created = ClinicalRequestSupport.toResponse(anamnesisService.register(
                patientId, ClinicalRequestSupport.currentUserId(), toContent(body),
                ClinicalRequestSupport.contextOf(request)));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PostMapping("/{entryId}/corrections")
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<AnamnesisEntryResponse> correct(
            @PathVariable UUID patientId,
            @PathVariable UUID entryId,
            @Valid @RequestBody AnamnesisCorrectionRequest body,
            HttpServletRequest request) {
        AnamnesisEntryResponse created = ClinicalRequestSupport.toResponse(anamnesisService.correct(
                patientId, entryId, body.reason(), toContent(body.content()),
                ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request)));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    private static AnamnesisContent toContent(AnamnesisRequest body) {
        return AnamnesisContent.of(
                body.allergies(), body.medications(), body.medicalConditions(), body.habits(), body.notes());
    }
}
