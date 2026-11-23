package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de las evoluciones: solo expone alta y lectura; la entidad es inmutable y no hay borrado. */
@Repository
class EvolutionEntryRepositoryAdapter implements EvolutionEntryRepository {

    private final SpringDataEvolutionEntryRepository delegate;

    EvolutionEntryRepositoryAdapter(SpringDataEvolutionEntryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvolutionEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return delegate.findByIdAndClinicalRecordId(entryId, clinicalRecordId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCorrected(UUID entryId) {
        return delegate.existsByCorrectsEntryId(entryId);
    }

    @Override
    @Transactional
    public EvolutionEntry save(EvolutionEntry entry) {
        return delegate.saveAndFlush(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvolutionEntry> findNewestFirst(UUID clinicalRecordId) {
        return delegate.findNewestFirst(clinicalRecordId);
    }
}
