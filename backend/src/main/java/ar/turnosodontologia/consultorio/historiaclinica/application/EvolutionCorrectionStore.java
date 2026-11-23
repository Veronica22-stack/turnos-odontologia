package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Almacen de evoluciones para las correcciones: toda evolucion no corregida es corregible (cadenas permitidas). */
@Component
public class EvolutionCorrectionStore implements CorrectableEntryStore<EvolutionEntry> {

    private final EvolutionEntryRepository evolutions;

    public EvolutionCorrectionStore(EvolutionEntryRepository evolutions) {
        this.evolutions = evolutions;
    }

    @Override
    public Optional<EvolutionEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return evolutions.findInRecord(clinicalRecordId, entryId);
    }

    @Override
    public boolean isCorrected(UUID entryId) {
        return evolutions.isCorrected(entryId);
    }

    @Override
    public EvolutionEntry save(EvolutionEntry entry) {
        return evolutions.save(entry);
    }
}
