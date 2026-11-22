package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.FakePatients;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClinicalRecordServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID activePatient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();

    private FakePatients patients;
    private InMemoryRecords records;
    private InMemoryAnamnesis anamnesis;
    private RecordingAudit audit;
    private ClinicalRecordService service;

    @BeforeEach
    void setUp() {
        patients = new FakePatients().add(activePatient, true).add(inactivePatient, false);
        records = new InMemoryRecords();
        anamnesis = new InMemoryAnamnesis();
        audit = new RecordingAudit();
        service = new ClinicalRecordService(
                new ClinicalPatientGuard(patients), records, anamnesis, ClinicalFakes.practice(), audit,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lecturaSinHistoriaClinicaNoLaAbreYAuditaHcRead() {
        ClinicalRecordSummaryData summary = service.summary(activePatient, USER_ID, CONTEXT);

        assertThat(summary.opened()).isFalse();
        assertThat(summary.clinicalRecordId()).isNull();
        assertThat(summary.currentAnamnesis()).isNull();
        assertThat(records.byPatient).isEmpty();
        assertThat(audit.events).hasSize(1);
        AuditEvent event = audit.events.get(0);
        assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.patientId()).isEqualTo(activePatient);
        assertThat(event.entityType()).isEqualTo("clinical_record");
        assertThat(event.ip()).isEqualTo("203.0.113.7");
        assertThat(event.userAgent()).isEqualTo("JUnit");
    }

    @Test
    void lecturaConHistoriaClinicaInformaAperturaYAnamnesisVigente() {
        ClinicalRecord record = service.openForWrite(activePatient, USER_ID);
        anamnesis.save(AnamnesisEntry.register(record.id(), 1, AnamnesisContent.of("a", null, null, null, null),
                new EntryProvenance(USER_ID, NOW)));
        anamnesis.save(AnamnesisEntry.register(record.id(), 2, AnamnesisContent.of("b", null, null, null, null),
                new EntryProvenance(USER_ID, NOW)));

        ClinicalRecordSummaryData summary = service.summary(activePatient, USER_ID, CONTEXT);

        assertThat(summary.opened()).isTrue();
        assertThat(summary.clinicalRecordId()).isEqualTo(record.id());
        assertThat(summary.openedAt()).isEqualTo(NOW);
        assertThat(summary.currentAnamnesis().version()).isEqualTo(2);
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityId()).isEqualTo(record.id().toString());
        });
    }

    @Test
    void pacienteInexistenteDevuelve404SinAuditoriaNiApertura() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.summary(unknown, USER_ID, CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PATIENT_NOT_FOUND");
        assertThatThrownBy(() -> service.openForWrite(unknown, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PATIENT_NOT_FOUND");
        assertThat(audit.events).isEmpty();
        assertThat(records.byPatient).isEmpty();
    }

    @Test
    void pacienteInactivoPermiteLeerPeroNoEscribir() {
        assertThat(service.summary(inactivePatient, USER_ID, CONTEXT).opened()).isFalse();
        assertThat(audit.events).hasSize(1);

        assertThatThrownBy(() -> service.openForWrite(inactivePatient, USER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PATIENT_INACTIVE");
        assertThat(records.byPatient).isEmpty();
    }

    @Test
    void laAperturaUsaElRelojYElActorYEsIdempotente() {
        ClinicalRecord first = service.openForWrite(activePatient, USER_ID);
        ClinicalRecord second = service.openForWrite(activePatient, UUID.randomUUID());

        assertThat(first.openedAt()).isEqualTo(NOW);
        assertThat(first.openedBy()).isEqualTo(USER_ID);
        assertThat(first.practiceId()).isEqualTo(ClinicalFakes.PRACTICE_ID);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(records.byPatient).hasSize(1);
    }

    @Test
    void siFallaLaAuditoriaLaLecturaNoEntregaDatos() {
        audit.failWith(new BusinessRuleException("AUDIT_WRITE_FAILED"));

        assertThatThrownBy(() -> service.summary(activePatient, USER_ID, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED");
    }
}
