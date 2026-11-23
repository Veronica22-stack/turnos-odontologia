package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecordRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionEntryRepository;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntryRepository;
import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Dobles de prueba en memoria de los puertos de la historia clinica (sin Spring ni base de datos). */
final class ClinicalFakes {

    static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    private ClinicalFakes() {
    }

    static InstallationPractice practice() {
        return new InstallationPractice(null) {
            @Override
            public UUID id() {
                return PRACTICE_ID;
            }
        };
    }

    static final class FakePatients implements PatientLookup {
        private final Map<UUID, Boolean> patients = new HashMap<>();

        FakePatients add(UUID id, boolean active) {
            patients.put(id, active);
            return this;
        }

        @Override
        public Optional<PatientStatus> findStatus(UUID patientId) {
            return Optional.ofNullable(patients.get(patientId)).map(active -> new PatientStatus(patientId, active));
        }
    }

    static final class RecordingAudit implements AuditLogger {
        final List<AuditEvent> events = new ArrayList<>();
        private RuntimeException failure;

        void failWith(RuntimeException error) {
            this.failure = error;
        }

        @Override
        public void log(AuditEvent event) {
            if (failure != null) {
                throw failure;
            }
            events.add(event);
        }
    }

    static final class InMemoryRecords implements ClinicalRecordRepository {
        final Map<UUID, ClinicalRecord> byPatient = new HashMap<>();

        @Override
        public Optional<ClinicalRecord> findByPatientId(UUID patientId) {
            return Optional.ofNullable(byPatient.get(patientId));
        }

        @Override
        public ClinicalRecord openIfAbsent(UUID practiceId, UUID patientId, UUID openedBy, Instant openedAt) {
            return byPatient.computeIfAbsent(patientId,
                    id -> ClinicalRecord.open(UUID.randomUUID(), practiceId, patientId, openedAt, openedBy));
        }
    }

    static final class InMemoryAnamnesis implements AnamnesisEntryRepository {
        final List<AnamnesisEntry> entries = new ArrayList<>();
        private RuntimeException saveFailure;

        void failSaveWith(String constraintName) {
            this.saveFailure = new DataIntegrityViolationException("violates unique constraint \"" + constraintName + "\"");
        }

        @Override
        public Optional<AnamnesisEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return entries.stream()
                    .filter(e -> e.id().equals(entryId) && e.clinicalRecordId().equals(clinicalRecordId))
                    .findFirst();
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return entries.stream().anyMatch(e -> entryId.equals(e.correctsEntryId()));
        }

        @Override
        public AnamnesisEntry save(AnamnesisEntry entry) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
            entries.add(entry);
            return entry;
        }

        @Override
        public List<AnamnesisEntry> findNewestFirst(UUID clinicalRecordId) {
            return entries.stream()
                    .filter(e -> e.clinicalRecordId().equals(clinicalRecordId))
                    .sorted(Comparator.comparingInt(AnamnesisEntry::version).reversed())
                    .toList();
        }

        @Override
        public Map<UUID, UUID> correctedByIndex(UUID clinicalRecordId) {
            Map<UUID, UUID> index = new HashMap<>();
            findNewestFirst(clinicalRecordId).stream()
                    .filter(e -> e.correctsEntryId() != null)
                    .forEach(e -> index.put(e.correctsEntryId(), e.id()));
            return index;
        }

        @Override
        public int maxVersion(UUID clinicalRecordId) {
            return entries.stream().filter(e -> e.clinicalRecordId().equals(clinicalRecordId))
                    .mapToInt(AnamnesisEntry::version).max().orElse(0);
        }
    }

    /** Odontograma en memoria; los ids crecen con el orden de alta para desempatar entradas del mismo instante. */
    static final class InMemoryOdontogram implements OdontogramEntryRepository {
        final List<OdontogramEntry> entries = new ArrayList<>();
        private long sequence;

        @Override
        public Optional<OdontogramEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return entries.stream()
                    .filter(e -> e.id().equals(entryId) && e.clinicalRecordId().equals(clinicalRecordId))
                    .findFirst();
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return entries.stream().anyMatch(e -> entryId.equals(e.correctsEntryId()));
        }

        @Override
        public OdontogramEntry save(OdontogramEntry entry) {
            ReflectionTestUtils.setField(entry, "id", new UUID(0L, ++sequence));
            entries.add(entry);
            return entry;
        }

        @Override
        public List<OdontogramEntry> findNewestFirst(UUID clinicalRecordId) {
            return entries.stream()
                    .filter(e -> e.clinicalRecordId().equals(clinicalRecordId))
                    .sorted(Comparator.comparing(OdontogramEntry::createdAt).thenComparing(OdontogramEntry::id).reversed())
                    .toList();
        }

        @Override
        public boolean existsNewerAtPosition(UUID clinicalRecordId, OdontogramEntry reference) {
            return entries.stream()
                    .filter(e -> e.clinicalRecordId().equals(clinicalRecordId))
                    .filter(e -> e.position().equals(reference.position()))
                    .anyMatch(e -> e.createdAt().isAfter(reference.createdAt())
                            || (e.createdAt().equals(reference.createdAt()) && e.id().compareTo(reference.id()) > 0));
        }
    }

    /** Evoluciones en memoria; los ids crecen con el orden de alta para desempatar entradas del mismo instante. */
    static final class InMemoryEvolutions implements EvolutionEntryRepository {
        final List<EvolutionEntry> entries = new ArrayList<>();
        private long sequence;
        private RuntimeException saveFailure;

        void failSaveWith(String constraintName) {
            this.saveFailure = new DataIntegrityViolationException("violates unique constraint \"" + constraintName + "\"");
        }

        @Override
        public Optional<EvolutionEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return entries.stream()
                    .filter(e -> e.id().equals(entryId) && e.clinicalRecordId().equals(clinicalRecordId))
                    .findFirst();
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return entries.stream().anyMatch(e -> entryId.equals(e.correctsEntryId()));
        }

        @Override
        public EvolutionEntry save(EvolutionEntry entry) {
            if (saveFailure != null) {
                throw saveFailure;
            }
            ReflectionTestUtils.setField(entry, "id", new UUID(2L, ++sequence));
            entries.add(entry);
            return entry;
        }

        @Override
        public List<EvolutionEntry> findNewestFirst(UUID clinicalRecordId) {
            return entries.stream()
                    .filter(e -> e.clinicalRecordId().equals(clinicalRecordId))
                    .sorted(Comparator.comparing(EvolutionEntry::createdAt).thenComparing(EvolutionEntry::id).reversed())
                    .toList();
        }
    }
}
