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
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnamnesisServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();

    private InMemoryRecords records;
    private InMemoryAnamnesis anamnesis;
    private RecordingAudit audit;
    private AnamnesisService service;

    @BeforeEach
    void setUp() {
        FakePatients patients = new FakePatients().add(patient, true).add(inactivePatient, false);
        ClinicalPatientGuard guard = new ClinicalPatientGuard(patients);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        records = new InMemoryRecords();
        anamnesis = new InMemoryAnamnesis();
        audit = new RecordingAudit();
        ClinicalRecordService recordService = new ClinicalRecordService(
                guard, records, anamnesis, ClinicalFakes.practice(), audit, clock);
        ClinicalCorrections corrections = new ClinicalCorrections(guard, records, audit, clock);
        service = new AnamnesisService(
                guard, recordService, records, anamnesis, new AnamnesisCorrectionStore(anamnesis),
                corrections, audit, clock);
    }

    @Test
    void laPrimeraAnamnesisAbreLaHistoriaYEsVersionUno() {
        AnamnesisEntryData created = service.register(patient, USER_ID, content("penicilina"), CONTEXT);

        assertThat(created.version()).isEqualTo(1);
        assertThat(created.authorId()).isEqualTo(USER_ID);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.correctsEntryId()).isNull();
        assertThat(records.byPatient).hasSize(1);
        assertThat(anamnesis.entries).hasSize(1);
    }

    @Test
    void unaNuevaVersionNoAlteraLaAnterior() {
        AnamnesisEntryData v1 = service.register(patient, USER_ID, content("penicilina"), CONTEXT);
        AnamnesisEntryData v2 = service.register(patient, USER_ID, content("latex"), CONTEXT);

        assertThat(v2.version()).isEqualTo(2);
        assertThat(records.byPatient).hasSize(1);
        AnamnesisData read = service.read(patient, USER_ID, CONTEXT);
        assertThat(read.current().id()).isEqualTo(v2.id());
        assertThat(read.history()).extracting(AnamnesisEntryData::version).containsExactly(2, 1);
        assertThat(read.history().get(1).id()).isEqualTo(v1.id());
        assertThat(read.history().get(1).content().allergies()).isEqualTo("penicilina");
        assertThat(read.history().get(1).correctedByEntryId()).isNull();
    }

    @Test
    void auditaHcCreateConLaNuevaEntradaYSoloTipoYVersion() {
        AnamnesisEntryData created = service.register(patient, USER_ID, content("penicilina"), CONTEXT);

        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CREATE);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.entityType()).isEqualTo("anamnesis_entry");
            assertThat(event.entityId()).isEqualTo(created.id().toString());
            assertThat(event.ip()).isEqualTo("203.0.113.7");
            assertThat(event.details()).isEqualTo(Map.of("entry_type", "ANAMNESIS", "version", 1));
        });
    }

    @Test
    void historialDescendenteMarcaLaEntradaCorregidaYLaVigenteEsLaUltima() {
        AnamnesisEntryData v1 = service.register(patient, USER_ID, content("penicilina"), CONTEXT);
        AnamnesisEntryData v2 = service.correct(patient, v1.id(), "alergia mal cargada", content("sin alergias"),
                USER_ID, CONTEXT);

        AnamnesisData read = service.read(patient, USER_ID, CONTEXT);

        assertThat(read.current().id()).isEqualTo(v2.id());
        assertThat(read.current().correctsEntryId()).isEqualTo(v1.id());
        assertThat(read.current().correctionReason()).isEqualTo("alergia mal cargada");
        assertThat(read.history()).extracting(AnamnesisEntryData::version).containsExactly(2, 1);
        assertThat(read.history().get(1).correctedByEntryId()).isEqualTo(v2.id());
    }

    @Test
    void sinAnamnesisLaLecturaDevuelveVigenteNulaHistorialVacioYAudita() {
        AnamnesisData read = service.read(patient, USER_ID, CONTEXT);

        assertThat(read.current()).isNull();
        assertThat(read.history()).isEmpty();
        assertThat(records.byPatient).isEmpty();
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityType()).isEqualTo("anamnesis_entry");
        });
    }

    @Test
    void laLecturaAuditaUnSoloHcReadConLaEntradaVigente() {
        AnamnesisEntryData v1 = service.register(patient, USER_ID, content("penicilina"), CONTEXT);
        audit.events.clear();

        service.read(patient, USER_ID, CONTEXT);

        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityId()).isEqualTo(v1.id().toString());
        });
    }

    @Test
    void unaViolacionDeVersionUnicaSeTraduceAEscrituraConcurrente() {
        anamnesis.failSaveWith("anamnesis_entry_record_version_unique");

        assertThatThrownBy(() -> service.register(patient, USER_ID, content("x"), CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_RECORD_CONCURRENT_WRITE");
        assertThat(audit.events).isEmpty();
    }

    @Test
    void pacienteInactivoRechazaElAltaSinCrearHistoriaYPacienteInexistenteDa404() {
        assertThatThrownBy(() -> service.register(inactivePatient, USER_ID, content("x"), CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PATIENT_INACTIVE");
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), USER_ID, content("x"), CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PATIENT_NOT_FOUND");
        assertThat(records.byPatient).isEmpty();
        assertThat(anamnesis.entries).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void corregirLaVigenteCreaLaVersionSiguienteQueLaReferencia() {
        service.register(patient, USER_ID, content("v1"), CONTEXT);
        service.register(patient, USER_ID, content("v2"), CONTEXT);
        AnamnesisEntryData v3 = service.register(patient, USER_ID, content("v3"), CONTEXT);

        AnamnesisEntryData v4 = service.correct(patient, v3.id(), "motivo", content("v3 corregida"), USER_ID, CONTEXT);

        assertThat(v4.version()).isEqualTo(4);
        assertThat(v4.correctsEntryId()).isEqualTo(v3.id());
        assertThat(v4.content().allergies()).isEqualTo("v3 corregida");
        assertThat(anamnesis.isCorrected(v3.id())).isTrue();
    }

    @Test
    void corregirUnaVersionSuperadaDa409NotCurrentSinCrearNada() {
        AnamnesisEntryData v1 = service.register(patient, USER_ID, content("v1"), CONTEXT);
        service.register(patient, USER_ID, content("v2"), CONTEXT);
        audit.events.clear();

        assertThatThrownBy(() -> service.correct(patient, v1.id(), "motivo", content("x"), USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_NOT_CURRENT");
        assertThat(anamnesis.entries).hasSize(2);
        assertThat(audit.events).isEmpty();
    }

    private static AnamnesisContent content(String allergies) {
        return AnamnesisContent.of(allergies, null, null, null, null);
    }
}
