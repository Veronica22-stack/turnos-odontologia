package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador del odontograma: solo expone alta y lectura; la entidad es inmutable y no hay borrado. */
@Repository
class OdontogramEntryRepositoryAdapter implements OdontogramEntryRepository {

    private final SpringDataOdontogramEntryRepository delegate;

    OdontogramEntryRepositoryAdapter(SpringDataOdontogramEntryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OdontogramEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
        return delegate.findByIdAndClinicalRecordId(entryId, clinicalRecordId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCorrected(UUID entryId) {
        return delegate.existsByCorrectsEntryId(entryId);
    }

    @Override
    @Transactional
    public OdontogramEntry save(OdontogramEntry entry) {
        return delegate.saveAndFlush(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OdontogramEntry> findNewestFirst(UUID clinicalRecordId) {
        return delegate.findNewestFirst(clinicalRecordId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsNewerAtPosition(UUID clinicalRecordId, OdontogramEntry reference) {
        ToothPosition position = reference.position();
        String toothCode = position.tooth().value();
        return position.hasSurface()
                ? delegate.existsNewerOnSurface(
                        clinicalRecordId, toothCode, position.surface(), reference.createdAt(), reference.id())
                : delegate.existsNewerWholeTooth(clinicalRecordId, toothCode, reference.createdAt(), reference.id());
    }
}
