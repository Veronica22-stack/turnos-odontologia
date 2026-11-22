package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.FakePatients;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntryType;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.CorrectableEntryStore;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El mecanismo generico de correcciones se prueba sobre un almacen falso, sin depender de un tipo concreto. */
class ClinicalCorrectionsTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID otherPatient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();

    private InMemoryRecords records;
    private InMemoryAnamnesis delegate;
    private SpyStore store;
    private RecordingAudit audit;
    private ClinicalCorrections corrections;
    private ClinicalRecord record;
    private ClinicalRecord otherRecord;

    @BeforeEach
    void setUp() {
        FakePatients patients = new FakePatients().add(patient, true).add(otherPatient, true).add(inactivePatient, false);
        records = new InMemoryRecords();
        delegate = new InMemoryAnamnesis();
        store = new SpyStore(delegate);
        audit = new RecordingAudit();
        corrections = new ClinicalCorrections(
                new ClinicalPatientGuard(patients), records, audit, Clock.fixed(NOW, ZoneOffset.UTC));
        record = records.openIfAbsent(ClinicalFakes.PRACTICE_ID, patient, USER_ID, NOW);
        otherRecord = records.openIfAbsent(ClinicalFakes.PRACTICE_ID, otherPatient, USER_ID, NOW);
    }

    @Test
    void correccionValidaCreaLaEntradaConReferenciaYMotivoYAuditaSinElMotivo() {
        AnamnesisEntry original = seed(record, 1);

        AnamnesisEntry created = correct(patient, original.id(), "alergia mal cargada");

        assertThat(created.correctsEntryId()).isEqualTo(original.id());
        assertThat(created.correctionReason()).isEqualTo("alergia mal cargada");
        assertThat(created.clinicalRecordId()).isEqualTo(record.id());
        assertThat(created.authorId()).isEqualTo(USER_ID);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CORRECT);
            assertThat(event.entityType()).isEqualTo("anamnesis_entry");
            assertThat(event.entityId()).isEqualTo(created.id().toString());
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.details()).isEqualTo(Map.of(
                    "entry_type", "ANAMNESIS", "corrects_entry_id", original.id().toString()));
            assertThat(event.details().toString()).doesNotContain("alergia mal cargada");
        });
    }

    @Test
    void entradaInexistenteODeOtraHistoriaDa404SinCrearNada() {
        AnamnesisEntry foreign = seed(otherRecord, 1);

        assertThatThrownBy(() -> correct(patient, UUID.randomUUID(), "motivo"))
                .isInstanceOf(NotFoundException.class).hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        assertThatThrownBy(() -> correct(patient, foreign.id(), "motivo"))
                .isInstanceOf(NotFoundException.class).hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        assertThat(delegate.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void pacienteSinHistoriaClinicaDa404DeEntradaNoEncontrada() {
        UUID noRecordPatient = UUID.randomUUID();
        FakePatients patients = new FakePatients().add(noRecordPatient, true);
        ClinicalCorrections other = new ClinicalCorrections(
                new ClinicalPatientGuard(patients), records, audit, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> other.correct(ClinicalEntryType.ANAMNESIS, store, noRecordPatient, UUID.randomUUID(),
                "motivo", USER_ID, CONTEXT, factory()))
                .isInstanceOf(NotFoundException.class).hasMessage("CLINICAL_ENTRY_NOT_FOUND");
    }

    @Test
    void pacienteInexistenteDa404YPacienteInactivoDa409() {
        assertThatThrownBy(() -> correct(UUID.randomUUID(), UUID.randomUUID(), "motivo"))
                .isInstanceOf(NotFoundException.class).hasMessage("PATIENT_NOT_FOUND");
        assertThatThrownBy(() -> correct(inactivePatient, UUID.randomUUID(), "motivo"))
                .isInstanceOf(ConflictException.class).hasMessage("PATIENT_INACTIVE");
        assertThat(audit.events).isEmpty();
    }

    @Test
    void entradaYaCorregidaDa409YNoInvocaElGancho() {
        AnamnesisEntry original = seed(record, 1);
        correct(patient, original.id(), "primera");
        store.assertCalls.clear();
        audit.events.clear();

        assertThatThrownBy(() -> correct(patient, original.id(), "segunda"))
                .isInstanceOf(ConflictException.class).hasMessage("CLINICAL_ENTRY_ALREADY_CORRECTED");
        assertThat(delegate.entries).hasSize(2);
        assertThat(store.assertCalls).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void elGanchoAssertCorrectableSeInvocaConLaOriginalYPuedeRechazar() {
        AnamnesisEntry original = seed(record, 1);

        correct(patient, original.id(), "motivo");
        assertThat(store.assertCalls).containsExactly(original);

        AnamnesisEntry another = seed(record, 5);
        store.reject = new ConflictException("CLINICAL_ENTRY_NOT_CURRENT");
        int before = delegate.entries.size();
        assertThatThrownBy(() -> correct(patient, another.id(), "motivo"))
                .isInstanceOf(ConflictException.class).hasMessage("CLINICAL_ENTRY_NOT_CURRENT");
        assertThat(delegate.entries).hasSize(before);
    }

    @Test
    void motivoInvalidoNoPersisteNiAudita() {
        AnamnesisEntry original = seed(record, 1);

        for (String invalid : new String[] {null, "", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> correct(patient, original.id(), invalid))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(delegate.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void unaViolacionDeCorreccionUnicaSeTraduceAEntradaYaCorregida() {
        AnamnesisEntry original = seed(record, 1);
        delegate.failSaveWith("anamnesis_entry_corrects_once_unique");

        assertThatThrownBy(() -> correct(patient, original.id(), "motivo"))
                .isInstanceOf(ConflictException.class).hasMessage("CLINICAL_ENTRY_ALREADY_CORRECTED");
        assertThat(audit.events).isEmpty();
    }

    private AnamnesisEntry correct(UUID patientId, UUID entryId, String reason) {
        return corrections.correct(
                ClinicalEntryType.ANAMNESIS, store, patientId, entryId, reason, USER_ID, CONTEXT, factory());
    }

    private CorrectionFactory<AnamnesisEntry> factory() {
        return (recordId, correction, provenance, original) -> AnamnesisEntry.correct(
                recordId, original.version() + 1, original.content(), correction, provenance);
    }

    private AnamnesisEntry seed(ClinicalRecord target, int version) {
        return delegate.save(AnamnesisEntry.register(
                target.id(), version, AnamnesisContent.of("x", null, null, null, null),
                new EntryProvenance(USER_ID, NOW)));
    }

    /** Almacen que registra las invocaciones al gancho y puede forzar su rechazo. */
    private static final class SpyStore implements CorrectableEntryStore<AnamnesisEntry> {
        private final InMemoryAnamnesis delegate;
        final List<AnamnesisEntry> assertCalls = new ArrayList<>();
        RuntimeException reject;

        SpyStore(InMemoryAnamnesis delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<AnamnesisEntry> findInRecord(UUID clinicalRecordId, UUID entryId) {
            return delegate.findInRecord(clinicalRecordId, entryId);
        }

        @Override
        public boolean isCorrected(UUID entryId) {
            return delegate.isCorrected(entryId);
        }

        @Override
        public AnamnesisEntry save(AnamnesisEntry entry) {
            return delegate.save(entry);
        }

        @Override
        public void assertCorrectable(AnamnesisEntry original) {
            assertCalls.add(original);
            if (reject != null) {
                throw reject;
            }
        }
    }
}
