package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.FakePatients;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryOdontogram;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalRecord;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothSurface;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Correccion del odontograma: solo la entrada vigente de su posicion, sin cambiar pieza ni cara (Q4). */
class OdontogramCorrectionTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID otherPatient = UUID.randomUUID();

    private InMemoryRecords records;
    private InMemoryOdontogram odontogram;
    private InMemoryAnamnesis anamnesis;
    private RecordingAudit audit;
    private OdontogramService service;

    @BeforeEach
    void setUp() {
        FakePatients patients = new FakePatients().add(patient, true).add(otherPatient, true);
        ClinicalPatientGuard guard = new ClinicalPatientGuard(patients);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        records = new InMemoryRecords();
        odontogram = new InMemoryOdontogram();
        anamnesis = new InMemoryAnamnesis();
        audit = new RecordingAudit();
        ClinicalRecordService recordService = new ClinicalRecordService(
                guard, records, anamnesis, ClinicalFakes.practice(), audit, clock);
        ClinicalCorrections corrections = new ClinicalCorrections(guard, records, audit, clock);
        service = new OdontogramService(
                guard, recordService, records, odontogram, new OdontogramCorrectionStore(odontogram), corrections,
                audit, clock);
    }

    @Test
    void corregirLaVigenteCreaUnaEntradaEnLaMismaPosicionConReferenciaYMotivo() {
        OdontogramEntryData original = register("46", null, ToothCondition.CARIES);
        audit.events.clear();

        OdontogramEntryData corrected = service.correct(
                patient, original.id(), "pieza sana, error de carga", ToothCondition.HEALTHY, "ok", USER_ID, CONTEXT);

        assertThat(corrected.toothCode()).isEqualTo("46");
        assertThat(corrected.surface()).isNull();
        assertThat(corrected.condition()).isEqualTo(ToothCondition.HEALTHY);
        assertThat(corrected.notes()).isEqualTo("ok");
        assertThat(corrected.correctsEntryId()).isEqualTo(original.id());
        assertThat(corrected.correctionReason()).isEqualTo("pieza sana, error de carga");
        assertThat(corrected.authorId()).isEqualTo(USER_ID);
        assertThat(odontogram.entries).hasSize(2);
        assertThat(odontogram.isCorrected(original.id())).isTrue();
    }

    @Test
    void laCorreccionAuditaHcCorrectSinMotivoNiContenido() {
        OdontogramEntryData original = register("46", null, ToothCondition.CARIES);
        audit.events.clear();

        OdontogramEntryData corrected = service.correct(
                patient, original.id(), "motivo clinico", ToothCondition.HEALTHY, "nota clinica", USER_ID, CONTEXT);

        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CORRECT);
            assertThat(event.entityType()).isEqualTo("odontogram_entry");
            assertThat(event.entityId()).isEqualTo(corrected.id().toString());
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.details()).isEqualTo(Map.of(
                    "entry_type", "ODONTOGRAM", "corrects_entry_id", original.id().toString()));
        });
    }

    @Test
    void laCorreccionConservaLaCaraDeLaOriginalYElNuevoEstadoLaAdmite() {
        OdontogramEntryData original = register("36", ToothSurface.OCCLUSAL, ToothCondition.CARIES);

        OdontogramEntryData corrected = service.correct(
                patient, original.id(), "era obturacion", ToothCondition.FILLED, null, USER_ID, CONTEXT);

        assertThat(corrected.surface()).isEqualTo(ToothSurface.OCCLUSAL);
        assertThat(corrected.condition()).isEqualTo(ToothCondition.FILLED);
    }

    @Test
    void despuesDeCorregirElEstadoActualEsLaCorreccionYElHistorialConservaLaOriginal() {
        OdontogramEntryData original = register("46", null, ToothCondition.CARIES);
        OdontogramEntryData corrected = service.correct(
                patient, original.id(), "motivo", ToothCondition.HEALTHY, null, USER_ID, CONTEXT);

        OdontogramData data = service.read(patient, USER_ID, CONTEXT);

        assertThat(data.current()).extracting(OdontogramEntryData::id).containsExactly(corrected.id());
        assertThat(data.history()).extracting(OdontogramEntryData::id).containsExactly(corrected.id(), original.id());
        assertThat(data.history().get(1).correctedByEntryId()).isEqualTo(corrected.id());
    }

    @Test
    void corregirUnaEntradaSuperadaDa409NotCurrentSinPersistirNiAuditar() {
        OdontogramEntryData first = register("46", null, ToothCondition.CARIES);
        register("46", null, ToothCondition.CROWN);
        audit.events.clear();

        assertThatThrownBy(() -> service.correct(
                patient, first.id(), "motivo", ToothCondition.HEALTHY, null, USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_NOT_CURRENT");
        assertThat(odontogram.entries).hasSize(2);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void laSuperacionEsPorPosicionAsiQueOtraCaraOLaPiezaCompletaNoLaImpiden() {
        OdontogramEntryData occlusal = register("36", ToothSurface.OCCLUSAL, ToothCondition.CARIES);
        register("36", ToothSurface.MESIAL, ToothCondition.CARIES);
        register("36", null, ToothCondition.MISSING);
        register("37", ToothSurface.OCCLUSAL, ToothCondition.CARIES);

        OdontogramEntryData corrected = service.correct(
                patient, occlusal.id(), "motivo", ToothCondition.FILLED, null, USER_ID, CONTEXT);

        assertThat(corrected.toothCode()).isEqualTo("36");
        assertThat(corrected.surface()).isEqualTo(ToothSurface.OCCLUSAL);
    }

    @Test
    void corregirUnaEntradaYaCorregidaDa409AlreadyCorrected() {
        OdontogramEntryData original = register("46", null, ToothCondition.CARIES);
        service.correct(patient, original.id(), "motivo", ToothCondition.HEALTHY, null, USER_ID, CONTEXT);
        audit.events.clear();

        assertThatThrownBy(() -> service.correct(
                patient, original.id(), "otra vez", ToothCondition.CROWN, null, USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_ALREADY_CORRECTED");
        assertThat(odontogram.entries).hasSize(2);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void unaEntradaInexistenteDeOtraHistoriaOAnamnesisDa404() {
        OdontogramEntryData mine = register("46", null, ToothCondition.CARIES);
        ClinicalRecord otherRecord = records.openIfAbsent(ClinicalFakes.PRACTICE_ID, otherPatient, USER_ID, NOW);
        AnamnesisEntry anamnesisEntry = anamnesis.save(AnamnesisEntry.register(
                records.byPatient.get(patient).id(), 1, AnamnesisContent.of("x", null, null, null, null),
                new EntryProvenance(USER_ID, NOW)));
        assertThat(otherRecord).isNotNull();

        for (UUID unknown : List.of(UUID.randomUUID(), anamnesisEntry.id())) {
            assertThatThrownBy(() -> service.correct(
                    patient, unknown, "motivo", ToothCondition.HEALTHY, null, USER_ID, CONTEXT))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        }
        assertThatThrownBy(() -> service.correct(
                otherPatient, mine.id(), "motivo", ToothCondition.HEALTHY, null, USER_ID, CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        assertThat(odontogram.entries).hasSize(1);
    }

    @Test
    void estadoIncompatibleConLaCaraDa422SinPersistirNiAuditar() {
        OdontogramEntryData original = register("36", ToothSurface.OCCLUSAL, ToothCondition.CARIES);
        audit.events.clear();

        assertThatThrownBy(() -> service.correct(
                patient, original.id(), "motivo", ToothCondition.MISSING, null, USER_ID, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("ODONTOGRAM_SURFACE_NOT_ALLOWED");
        assertThat(odontogram.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void motivoInvalidoNoPersisteNiAudita() {
        OdontogramEntryData original = register("46", null, ToothCondition.CARIES);
        audit.events.clear();

        for (String reason : new String[] {null, "", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> service.correct(
                    patient, original.id(), reason, ToothCondition.HEALTHY, null, USER_ID, CONTEXT))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(odontogram.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    private OdontogramEntryData register(String tooth, ToothSurface surface, ToothCondition condition) {
        return service.register(patient, USER_ID, List.of(
                new OdontogramEntryCommand(new ToothPosition(ToothCode.of(tooth), surface), condition, null)),
                CONTEXT).get(0);
    }
}
