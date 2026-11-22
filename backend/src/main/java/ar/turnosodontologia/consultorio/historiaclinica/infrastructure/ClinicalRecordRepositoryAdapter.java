package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ClinicalRecordRepositoryAdapter implements ClinicalRecordRepository {

    private final SpringDataClinicalRecordRepository delegate;

    ClinicalRecordRepositoryAdapter(SpringDataClinicalRecordRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClinicalRecord> findByPatientId(UUID patientId) {
        return delegate.findByPatientId(patientId);
    }

    @Override
    @Transactional
    public ClinicalRecord openIfAbsent(UUID practiceId, UUID patientId, UUID openedBy, Instant openedAt) {
        delegate.insertIfAbsent(
                UuidVersion7Strategy.INSTANCE.generateUuid(null), practiceId, patientId, openedAt, openedBy);
        return delegate.findByPatientId(patientId)
                .orElseThrow(() -> new IllegalStateException("La historia clinica no existe tras abrirla"));
    }
}
