package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.api.ClinicalRecordSummaryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalRecordService;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalRecordSummaryData;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de la historia clinica de un paciente (solo lectura: la HC se abre al primer registro clinico). */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/clinical-record")
class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;

    ClinicalRecordController(ClinicalRecordService clinicalRecordService) {
        this.clinicalRecordService = clinicalRecordService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<ClinicalRecordSummaryResponse> get(@PathVariable UUID patientId, HttpServletRequest request) {
        ClinicalRecordSummaryData summary = clinicalRecordService.summary(
                patientId, ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(new ClinicalRecordSummaryResponse(
                        summary.opened(), summary.clinicalRecordId(), summary.openedAt(),
                        ClinicalRequestSupport.toResponse(summary.currentAnamnesis())));
    }
}
