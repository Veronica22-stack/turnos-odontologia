package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Almacen de anamnesis para las correcciones: solo la version vigente (la de mayor numero) es corregible (Q5). */
@Component
public class AnamnesisCorrectionStore implements CorrectableEntryStore<AnamnesisEntry> {

    private final AnamnesisEntryRepository anamnesis;

    public AnamnesisCorrectionStore(AnamnesisEntryRepository anamnesis) {
        this.anamnesis = anamnesis;
    }

    @Override
    public Optional<AnamnesisEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return anamnesis.findInRecord(clinicalRecordId, entryId);
    }

    @Override
    public boolean isCorrected(UUID entryId) {
        return anamnesis.isCorrected(entryId);
    }

    @Override
    public AnamnesisEntry save(AnamnesisEntry entry) {
        return anamnesis.save(entry);
    }

    @Override
    public void assertCorrectable(AnamnesisEntry original) {
        if (original.version() != anamnesis.maxVersion(original.clinicalRecordId())) {
            throw new ConflictException("CLINICAL_ENTRY_NOT_CURRENT");
        }
    }
}
