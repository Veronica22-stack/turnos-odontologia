package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.FakePatients;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryOdontogram;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdontogramServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();

    private InMemoryRecords records;
    private InMemoryOdontogram odontogram;
    private RecordingAudit audit;
    private OdontogramService service;

    @BeforeEach
    void setUp() {
        FakePatients patients = new FakePatients().add(patient, true).add(inactivePatient, false);
        ClinicalPatientGuard guard = new ClinicalPatientGuard(patients);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        records = new InMemoryRecords();
        odontogram = new InMemoryOdontogram();
        audit = new RecordingAudit();
        ClinicalRecordService recordService = new ClinicalRecordService(
                guard, records, new InMemoryAnamnesis(), ClinicalFakes.practice(), audit, clock);
        ClinicalCorrections corrections = new ClinicalCorrections(guard, records, audit, clock);
        service = new OdontogramService(
                guard, recordService, records, odontogram, new OdontogramCorrectionStore(odontogram), corrections,
                audit, clock);
    }

    @Test
    void elPrimerRegistroAbreLaHistoriaYGuardaLaEntradaConAutorYRelojDelServidor() {
        List<OdontogramEntryData> created = service.register(
                patient, USER_ID, List.of(command("16", ToothSurface.OCCLUSAL, ToothCondition.CARIES)), CONTEXT);

        assertThat(created).singleElement().satisfies(entry -> {
            assertThat(entry.toothCode()).isEqualTo("16");
            assertThat(entry.surface()).isEqualTo(ToothSurface.OCCLUSAL);
            assertThat(entry.condition()).isEqualTo(ToothCondition.CARIES);
            assertThat(entry.authorId()).isEqualTo(USER_ID);
            assertThat(entry.createdAt()).isEqualTo(NOW);
            assertThat(entry.correctsEntryId()).isNull();
        });
        assertThat(records.byPatient).hasSize(1);
        assertThat(odontogram.entries).hasSize(1);
    }

    @Test
    void unLoteCreaUnaEntradaPorEstadoYUnHcCreateCadaUnaSoloConElTipo() {
        List<OdontogramEntryData> created = service.register(patient, USER_ID, List.of(
                command("16", ToothSurface.OCCLUSAL, ToothCondition.CARIES),
                command("21", null, ToothCondition.CROWN),
                command("48", null, ToothCondition.MISSING)), CONTEXT);

        assertThat(created).hasSize(3);
        assertThat(odontogram.entries).hasSize(3);
        assertThat(audit.events).hasSize(3).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CREATE);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.entityType()).isEqualTo("odontogram_entry");
            assertThat(event.ip()).isEqualTo("203.0.113.7");
            assertThat(event.details()).isEqualTo(Map.of("entry_type", "ODONTOGRAM"));
        });
        assertThat(audit.events).extracting(e -> e.entityId())
                .containsExactlyInAnyOrderElementsOf(created.stream().map(e -> e.id().toString()).toList());
    }

    @Test
    void unNuevoRegistroNoAlteraLasEntradasPreviasNiAbreOtraHistoria() {
        service.register(patient, USER_ID, List.of(command("36", null, ToothCondition.CARIES)), CONTEXT);
        service.register(patient, USER_ID, List.of(command("36", null, ToothCondition.CROWN)), CONTEXT);

        assertThat(records.byPatient).hasSize(1);
        assertThat(odontogram.entries).hasSize(2);
        assertThat(odontogram.entries).extracting(e -> e.condition())
                .containsExactly(ToothCondition.CARIES, ToothCondition.CROWN);
    }

    @Test
    void pacienteInactivoDa409SinHistoriaEntradasNiAuditoria() {
        assertThatThrownBy(() -> service.register(
                inactivePatient, USER_ID, List.of(command("11", null, ToothCondition.CARIES)), CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PATIENT_INACTIVE");

        assertThat(records.byPatient).isEmpty();
        assertThat(odontogram.entries).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void pacienteInexistenteDa404SinHistoriaEntradasNiAuditoria() {
        assertThatThrownBy(() -> service.register(
                UUID.randomUUID(), USER_ID, List.of(command("11", null, ToothCondition.CARIES)), CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PATIENT_NOT_FOUND");

        assertThat(records.byPatient).isEmpty();
        assertThat(odontogram.entries).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void sinHistoriaLaLecturaDevuelveVacioNoAbreLaHistoriaYAuditaUnHcRead() {
        OdontogramData data = service.read(patient, USER_ID, CONTEXT);

        assertThat(data.current()).isEmpty();
        assertThat(data.history()).isEmpty();
        assertThat(records.byPatient).isEmpty();
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityType()).isEqualTo("odontogram_entry");
            assertThat(event.entityId()).isNull();
            assertThat(event.details()).isEmpty();
        });
    }

    @Test
    void laLecturaDevuelveElEstadoActualDerivadoYElHistorialDescendente() {
        service.register(patient, USER_ID, List.of(command("36", ToothSurface.OCCLUSAL, ToothCondition.CARIES)), CONTEXT);
        service.register(patient, USER_ID, List.of(command("36", ToothSurface.OCCLUSAL, ToothCondition.FILLED)), CONTEXT);
        service.register(patient, USER_ID, List.of(command("11", null, ToothCondition.MISSING)), CONTEXT);
        audit.events.clear();

        OdontogramData data = service.read(patient, USER_ID, CONTEXT);

        assertThat(data.current()).extracting(OdontogramEntryData::toothCode).containsExactly("11", "36");
        assertThat(data.current().get(1).condition()).isEqualTo(ToothCondition.FILLED);
        assertThat(data.history()).extracting(OdontogramEntryData::condition)
                .containsExactly(ToothCondition.MISSING, ToothCondition.FILLED, ToothCondition.CARIES);
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityType()).isEqualTo("odontogram_entry");
        });
    }

    @Test
    void laLecturaDeUnPacienteInactivoEsPosibleYUnInexistenteDa404SinAuditoria() {
        service.register(patient, USER_ID, List.of(command("11", null, ToothCondition.CARIES)), CONTEXT);
        audit.events.clear();

        assertThat(service.read(inactivePatient, USER_ID, CONTEXT).history()).isEmpty();
        assertThat(audit.events).hasSize(1);
        audit.events.clear();

        assertThatThrownBy(() -> service.read(UUID.randomUUID(), USER_ID, CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("PATIENT_NOT_FOUND");
        assertThat(audit.events).isEmpty();
    }

    @Test
    void siFallaLaAuditoriaDelLoteLaExcepcionSePropaga() {
        audit.failWith(new IllegalStateException("audit down"));

        assertThatThrownBy(() -> service.register(
                patient, USER_ID, List.of(command("11", null, ToothCondition.CARIES)), CONTEXT))
                .isInstanceOf(IllegalStateException.class);
    }

    private static OdontogramEntryCommand command(String tooth, ToothSurface surface, ToothCondition condition) {
        return new OdontogramEntryCommand(new ToothPosition(ToothCode.of(tooth), surface), condition, null);
    }
}
