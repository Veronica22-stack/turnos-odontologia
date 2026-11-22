package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Almacen del odontograma para las correcciones: solo la entrada vigente de su posicion es corregible (Q4). */
@Component
public class OdontogramCorrectionStore implements CorrectableEntryStore<OdontogramEntry> {

    private final OdontogramEntryRepository odontogram;

    public OdontogramCorrectionStore(OdontogramEntryRepository odontogram) {
        this.odontogram = odontogram;
    }

    @Override
    public Optional<OdontogramEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return odontogram.findInRecord(clinicalRecordId, entryId);
    }

    @Override
    public boolean isCorrected(UUID entryId) {
        return odontogram.isCorrected(entryId);
    }

    @Override
    public OdontogramEntry save(OdontogramEntry entry) {
        return odontogram.save(entry);
    }

    @Override
    public void assertCorrectable(OdontogramEntry original) {
        if (odontogram.existsNewerAtPosition(original.clinicalRecordId(), original)) {
            throw new ConflictException("CLINICAL_ENTRY_NOT_CURRENT");
        }
    }
}
