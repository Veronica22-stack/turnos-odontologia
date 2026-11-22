package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;

/** Traduce las violaciones de restricciones de la base (carreras entre escrituras) a errores de dominio 409. */
final class ClinicalWriteConflicts {

    private ClinicalWriteConflicts() {
    }

    static RuntimeException translate(DataIntegrityViolationException error) {
        String message = error.getMessage();
        if (message != null && message.contains("corrects_once_unique")) {
            return new ConflictException("CLINICAL_ENTRY_ALREADY_CORRECTED");
        }
        if (message != null && message.contains("record_version_unique")) {
            return new ConflictException("CLINICAL_RECORD_CONCURRENT_WRITE");
        }
        return error;
    }
}
