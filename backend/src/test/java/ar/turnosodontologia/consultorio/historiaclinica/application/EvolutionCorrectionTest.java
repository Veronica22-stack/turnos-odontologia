package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.FakePatients;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryEvolutions;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryOdontogram;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import ar.turnosodontologia.consultorio.historiaclinica.domain.OdontogramEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCode;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Correccion de evoluciones: cualquier entrada no corregida es corregible (incluidas las correcciones). */
class EvolutionCorrectionTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID otherPatient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();
    private final UUID appointmentId = UUID.randomUUID();

    private InMemoryRecords records;
    private InMemoryEvolutions evolutions;
    private InMemoryOdontogram odontogram;
    private InMemoryAnamnesis anamnesis;
    private RecordingAudit audit;
    private FakePatients patients;
    private EvolutionService service;

    @BeforeEach
    void setUp() {
        patients = new FakePatients().add(patient, true).add(otherPatient, true).add(inactivePatient, true);
        ClinicalPatientGuard guard = new ClinicalPatientGuard(patients);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        records = new InMemoryRecords();
        evolutions = new InMemoryEvolutions();
        odontogram = new InMemoryOdontogram();
        anamnesis = new InMemoryAnamnesis();
        audit = new RecordingAudit();
        ClinicalRecordService recordService = new ClinicalRecordService(
                guard, records, anamnesis, ClinicalFakes.practice(), audit, clock);
        ClinicalCorrections corrections = new ClinicalCorrections(guard, records, audit, clock);
        AppointmentNotificationLookup lookup = id -> id.equals(appointmentId)
                ? Optional.of(new AppointmentNotificationView(id, patient, "Limpieza", NOW.minusSeconds(3600),
                        NOW.minusSeconds(1800), AppointmentStatusView.COMPLETED))
                : Optional.empty();
        service = new EvolutionService(
                guard, recordService, records, evolutions, new EvolutionAppointmentGuard(lookup, clock),
                new EvolutionCorrectionStore(evolutions), corrections, audit, clock);
    }

    @Test
    void corregirCreaUnaEntradaConReferenciaMotivoYElTurnoDeLaOriginal() {
        EvolutionEntryData original = register("texto erroneo", appointmentId);
        audit.events.clear();

        EvolutionEntryData corrected = service.correct(
                patient, original.id(), "error de tipeo", EvolutionDescription.of("texto correcto"), USER_ID, CONTEXT);

        assertThat(corrected.description()).isEqualTo("texto correcto");
        assertThat(corrected.appointmentId()).isEqualTo(appointmentId);
        assertThat(corrected.correctsEntryId()).isEqualTo(original.id());
        assertThat(corrected.correctionReason()).isEqualTo("error de tipeo");
        assertThat(corrected.authorId()).isEqualTo(USER_ID);
        assertThat(corrected.createdAt()).isEqualTo(NOW);
        assertThat(evolutions.entries).hasSize(2);
        assertThat(evolutions.isCorrected(original.id())).isTrue();
    }

    @Test
    void laCorreccionDeUnaEvolucionSinTurnoTampocoTieneTurno() {
        EvolutionEntryData original = register("sin turno", null);

        EvolutionEntryData corrected = service.correct(
                patient, original.id(), "motivo", EvolutionDescription.of("corregida"), USER_ID, CONTEXT);

        assertThat(corrected.appointmentId()).isNull();
    }

    @Test
    void laCorreccionAuditaHcCorrectSinMotivoNiContenido() {
        EvolutionEntryData original = register("original", null);
        audit.events.clear();

        EvolutionEntryData corrected = service.correct(
                patient, original.id(), "motivo clinico", EvolutionDescription.of("nuevo texto clinico"), USER_ID,
                CONTEXT);

        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CORRECT);
            assertThat(event.entityType()).isEqualTo("evolution_entry");
            assertThat(event.entityId()).isEqualTo(corrected.id().toString());
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.details()).isEqualTo(Map.of(
                    "entry_type", "EVOLUTION", "corrects_entry_id", original.id().toString()));
        });
    }

    @Test
    void sePuedeCorregirUnaCorreccionYAUnaEntradaCorregidaNo() {
        EvolutionEntryData a = register("A", null);
        EvolutionEntryData b = service.correct(patient, a.id(), "corrige A", EvolutionDescription.of("B"), USER_ID, CONTEXT);
        EvolutionEntryData c = service.correct(patient, b.id(), "corrige B", EvolutionDescription.of("C"), USER_ID, CONTEXT);
        audit.events.clear();

        assertThat(c.correctsEntryId()).isEqualTo(b.id());
        assertThatThrownBy(() -> service.correct(
                patient, a.id(), "otra vez", EvolutionDescription.of("D"), USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_ALREADY_CORRECTED");
        assertThatThrownBy(() -> service.correct(
                patient, b.id(), "otra vez", EvolutionDescription.of("E"), USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_ALREADY_CORRECTED");
        assertThat(evolutions.entries).hasSize(3);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void unIdInexistenteDeAnamnesisOdontogramaOOtraHistoriaDa404() {
        EvolutionEntryData mine = register("mia", null);
        records.openIfAbsent(ClinicalFakes.PRACTICE_ID, otherPatient, USER_ID, NOW);
        UUID recordId = records.byPatient.get(patient).id();
        AnamnesisEntry anamnesisEntry = anamnesis.save(AnamnesisEntry.register(
                recordId, 1, AnamnesisContent.of("x", null, null, null, null), new EntryProvenance(USER_ID, NOW)));
        OdontogramEntry odontogramEntry = odontogram.save(OdontogramEntry.register(
                recordId, new ToothPosition(ToothCode.of("36"), null), ToothCondition.CARIES, null,
                new EntryProvenance(USER_ID, NOW)));

        for (UUID unknown : List.of(UUID.randomUUID(), anamnesisEntry.id(), odontogramEntry.id())) {
            assertThatThrownBy(() -> service.correct(
                    patient, unknown, "motivo", EvolutionDescription.of("x"), USER_ID, CONTEXT))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        }
        assertThatThrownBy(() -> service.correct(
                otherPatient, mine.id(), "motivo", EvolutionDescription.of("x"), USER_ID, CONTEXT))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("CLINICAL_ENTRY_NOT_FOUND");
        assertThat(evolutions.entries).hasSize(1);
    }

    @Test
    void motivoInvalidoNoPersisteNiAudita() {
        EvolutionEntryData original = register("original", null);
        audit.events.clear();

        for (String reason : new String[] {null, "", "   ", "x".repeat(501)}) {
            assertThatThrownBy(() -> service.correct(
                    patient, original.id(), reason, EvolutionDescription.of("x"), USER_ID, CONTEXT))
                    .isInstanceOf(ValidationException.class);
        }
        assertThat(evolutions.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    @Test
    void pacienteInactivoDa409SinPersistirNiAuditar() {
        EvolutionEntryData original = register("original", null);
        patients.add(patient, false);
        audit.events.clear();

        assertThatThrownBy(() -> service.correct(
                patient, original.id(), "motivo", EvolutionDescription.of("x"), USER_ID, CONTEXT))
                .isInstanceOf(ConflictException.class)
                .hasMessage("PATIENT_INACTIVE");
        assertThat(evolutions.entries).hasSize(1);
        assertThat(audit.events).isEmpty();
    }

    private EvolutionEntryData register(String description, UUID appointment) {
        return service.register(
                patient, USER_ID, new EvolutionCommand(EvolutionDescription.of(description), appointment), CONTEXT);
    }
}
