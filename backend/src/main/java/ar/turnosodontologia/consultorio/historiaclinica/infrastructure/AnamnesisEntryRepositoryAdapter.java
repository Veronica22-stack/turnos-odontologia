package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de la anamnesis: solo expone alta y lectura; la entidad es inmutable y no hay borrado. */
@Repository
class AnamnesisEntryRepositoryAdapter implements AnamnesisEntryRepository {

    private final SpringDataAnamnesisEntryRepository delegate;

    AnamnesisEntryRepositoryAdapter(SpringDataAnamnesisEntryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnamnesisEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return delegate.findByIdAndClinicalRecordId(entryId, clinicalRecordId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCorrected(UUID entryId) {
        return delegate.existsByCorrectsEntryId(entryId);
    }

    @Override
    @Transactional
    public AnamnesisEntry save(AnamnesisEntry entry) {
        return delegate.saveAndFlush(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnamnesisEntry> findNewestFirst(UUID clinicalRecordId) {
        return delegate.findByClinicalRecordIdOrderByVersionDesc(clinicalRecordId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> correctedByIndex(UUID clinicalRecordId) {
        Map<UUID, UUID> index = new HashMap<>();
        for (AnamnesisEntry entry : delegate.findByClinicalRecordIdOrderByVersionDesc(clinicalRecordId)) {
            if (entry.correctsEntryId() != null) {
                index.put(entry.correctsEntryId(), entry.id());
            }
        }
        return index;
    }

    @Override
    @Transactional(readOnly = true)
    public int maxVersion(UUID clinicalRecordId) {
        return delegate.maxVersion(clinicalRecordId);
    }
}
