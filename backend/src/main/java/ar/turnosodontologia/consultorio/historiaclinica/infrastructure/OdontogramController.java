package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramCorrectionRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramEntriesResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramEntryRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramEntryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramRequest;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramResponse;
import ar.turnosodontologia.consultorio.historiaclinica.application.OdontogramData;
import ar.turnosodontologia.consultorio.historiaclinica.application.OdontogramEntryCommand;
import ar.turnosodontologia.consultorio.historiaclinica.application.OdontogramService;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
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
 * Odontograma del paciente: solo GET y POST (la historia clinica es append-only). Autor, instante e id los fija el
 * servidor; el cuerpo no los acepta, y una correccion no puede cambiar la pieza ni la cara.
 */
@RestController
@RequestMapping("/api/v1/patients/{patientId}/clinical-record/odontogram")
class OdontogramController {

    private final OdontogramService odontogramService;

    OdontogramController(OdontogramService odontogramService) {
        this.odontogramService = odontogramService;
    }

    @GetMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<OdontogramResponse> get(@PathVariable UUID patientId, HttpServletRequest request) {
        OdontogramData data = odontogramService.read(
                patientId, ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request));
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(new OdontogramResponse(
                        data.current().stream().map(ClinicalRequestSupport::toResponse).toList(),
                        data.history().stream().map(ClinicalRequestSupport::toResponse).toList()));
    }

    @PostMapping
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<OdontogramEntriesResponse> register(
            @PathVariable UUID patientId, @Valid @RequestBody OdontogramRequest body, HttpServletRequest request) {
        OdontogramEntriesResponse created = new OdontogramEntriesResponse(odontogramService.register(
                        patientId, ClinicalRequestSupport.currentUserId(),
                        body.entries().stream().map(OdontogramController::toCommand).toList(),
                        ClinicalRequestSupport.contextOf(request))
                .stream().map(ClinicalRequestSupport::toResponse).toList());
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    @PostMapping("/{entryId}/corrections")
    @PreAuthorize("hasRole('DENTIST')")
    ResponseEntity<OdontogramEntryResponse> correct(
            @PathVariable UUID patientId,
            @PathVariable UUID entryId,
            @Valid @RequestBody OdontogramCorrectionRequest body,
            HttpServletRequest request) {
        OdontogramEntryResponse created = ClinicalRequestSupport.toResponse(odontogramService.correct(
                patientId, entryId, body.reason(), ToothCondition.valueOf(body.condition()), body.notes(),
                ClinicalRequestSupport.currentUserId(), ClinicalRequestSupport.contextOf(request)));
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store").body(created);
    }

    private static OdontogramEntryCommand toCommand(OdontogramEntryRequest entry) {
        ToothSurface surface = entry.surface() == null ? null : ToothSurface.valueOf(entry.surface());
        return new OdontogramEntryCommand(
                new ToothPosition(ToothCode.of(entry.toothCode()), surface),
                ToothCondition.valueOf(entry.condition()), entry.notes());
    }
}
