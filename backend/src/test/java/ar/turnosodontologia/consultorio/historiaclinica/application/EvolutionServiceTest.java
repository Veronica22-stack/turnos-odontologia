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
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryRecords;
import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.RecordingAudit;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvolutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final RequestContext CONTEXT = new RequestContext("203.0.113.7", "JUnit");

    private final UUID patient = UUID.randomUUID();
    private final UUID otherPatient = UUID.randomUUID();
    private final UUID inactivePatient = UUID.randomUUID();
    private final Map<UUID, AppointmentNotificationView> appointments = new HashMap<>();

    private InMemoryRecords records;
    private InMemoryEvolutions evolutions;
    private RecordingAudit audit;
    private EvolutionService service;

    @BeforeEach
    void setUp() {
        FakePatients patients = new FakePatients()
                .add(patient, true).add(otherPatient, true).add(inactivePatient, false);
        ClinicalPatientGuard guard = new ClinicalPatientGuard(patients);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        records = new InMemoryRecords();
        evolutions = new InMemoryEvolutions();
        audit = new RecordingAudit();
        ClinicalRecordService recordService = new ClinicalRecordService(
                guard, records, new InMemoryAnamnesis(), ClinicalFakes.practice(), audit, clock);
        ClinicalCorrections corrections = new ClinicalCorrections(guard, records, audit, clock);
        AppointmentNotificationLookup lookup = id -> Optional.ofNullable(appointments.get(id));
        service = new EvolutionService(
                guard, recordService, records, evolutions, new EvolutionAppointmentGuard(lookup, clock),
                new EvolutionCorrectionStore(evolutions), corrections, audit, clock);
    }

    // ---- register ----

    @Test
    void elPrimerRegistroAbreLaHistoriaYGuardaLaEvolucionConAutorYRelojDelServidor() {
        EvolutionEntryData created = service.register(patient, USER_ID, command("Control de rutina", null), CONTEXT);

        assertThat(created.description()).isEqualTo("Control de rutina");
        assertThat(created.authorId()).isEqualTo(USER_ID);
        assertThat(created.createdAt()).isEqualTo(NOW);
        assertThat(created.appointmentId()).isNull();
        assertThat(created.correctsEntryId()).isNull();
        assertThat(records.byPatient).hasSize(1);
        assertThat(evolutions.entries).hasSize(1);
    }

    @Test
    void conTurnoValidoGuardaElVinculoYSinTurnoNoConsultaAgenda() {
        UUID appointmentId = appointment(patient, NOW.minusSeconds(3600), AppointmentStatusView.COMPLETED);

        EvolutionEntryData withAppointment = service.register(
                patient, USER_ID, command("con turno", appointmentId), CONTEXT);
        EvolutionEntryData without = service.register(patient, USER_ID, command("sin turno", null), CONTEXT);

        assertThat(withAppointment.appointmentId()).isEqualTo(appointmentId);
        assertThat(without.appointmentId()).isNull();
        assertThat(records.byPatient).hasSize(1);
        assertThat(evolutions.entries).hasSize(2);
    }

    @Test
    void cadaAltaAuditaUnHcCreateSoloConElTipoYElIdDeLaEntrada() {
        EvolutionEntryData first = service.register(patient, USER_ID, command("primera", null), CONTEXT);
        EvolutionEntryData second = service.register(patient, USER_ID, command("segunda", null), CONTEXT);

        assertThat(audit.events).hasSize(2).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_CREATE);
            assertThat(event.userId()).isEqualTo(USER_ID);
            assertThat(event.patientId()).isEqualTo(patient);
            assertThat(event.entityType()).isEqualTo("evolution_entry");
            assertThat(event.ip()).isEqualTo("203.0.113.7");
            assertThat(event.details()).isEqualTo(Map.of("entry_type", "EVOLUTION"));
        });
        assertThat(audit.events).extracting(e -> e.entityId())
                .containsExactly(first.id().toString(), second.id().toString());
    }

    @Test
    void unTurnoInvalidoFallaSinAbrirLaHistoriaNiGuardarNiAuditar() {
        UUID future = appointment(patient, NOW.plusSeconds(3600), AppointmentStatusView.CONFIRMED);
        UUID cancelled = appointment(patient, NOW.minusSeconds(3600), AppointmentStatusView.CANCELLED);
        UUID foreign = appointment(otherPatient, NOW.minusSeconds(3600), AppointmentStatusView.COMPLETED);

        assertThatThrownBy(() -> service.register(patient, USER_ID, command("x", future), CONTEXT))
                .isInstanceOf(BusinessRuleException.class).hasMessage("EVOLUTION_APPOINTMENT_NOT_STARTED");
        assertThatThrownBy(() -> service.register(patient, USER_ID, command("x", cancelled), CONTEXT))
                .isInstanceOf(BusinessRuleException.class).hasMessage("EVOLUTION_APPOINTMENT_NOT_ATTENDED");
        assertThatThrownBy(() -> service.register(patient, USER_ID, command("x", foreign), CONTEXT))
                .isInstanceOf(BusinessRuleException.class).hasMessage("EVOLUTION_APPOINTMENT_NOT_FOUND");
        assertThatThrownBy(() -> service.register(patient, USER_ID, command("x", UUID.randomUUID()), CONTEXT))
                .isInstanceOf(BusinessRuleException.class).hasMessage("EVOLUTION_APPOINTMENT_NOT_FOUND");

        assertThat(records.byPatient).isEmpty();
        assertThat(evolutions.entries).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void pacienteInactivoDa409YPacienteInexistenteDa404SinNadaPersistido() {
        assertThatThrownBy(() -> service.register(inactivePatient, USER_ID, command("x", null), CONTEXT))
                .isInstanceOf(ConflictException.class).hasMessage("PATIENT_INACTIVE");
        assertThatThrownBy(() -> service.register(UUID.randomUUID(), USER_ID, command("x", null), CONTEXT))
                .isInstanceOf(NotFoundException.class).hasMessage("PATIENT_NOT_FOUND");

        assertThat(records.byPatient).isEmpty();
        assertThat(evolutions.entries).isEmpty();
        assertThat(audit.events).isEmpty();
    }

    @Test
    void siFallaLaAuditoriaLaExcepcionSePropaga() {
        audit.failWith(new IllegalStateException("audit down"));

        assertThatThrownBy(() -> service.register(patient, USER_ID, command("x", null), CONTEXT))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- read ----

    @Test
    void sinHistoriaLaLecturaDevuelveVacioNoAbreLaHistoriaYAuditaUnHcRead() {
        List<EvolutionEntryData> data = service.read(patient, USER_ID, CONTEXT);

        assertThat(data).isEmpty();
        assertThat(records.byPatient).isEmpty();
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityType()).isEqualTo("evolution_entry");
            assertThat(event.entityId()).isNull();
            assertThat(event.details()).isEmpty();
        });
    }

    @Test
    void laLecturaDevuelveLaMasRecienteVaPrimeroConLaMarcaDeCorreccion() {
        EvolutionEntryData first = service.register(patient, USER_ID, command("primera", null), CONTEXT);
        EvolutionEntryData second = service.register(patient, USER_ID, command("segunda", null), CONTEXT);
        EvolutionEntryData correction = service.correct(
                patient, first.id(), "error de carga", EvolutionDescription.of("primera corregida"), USER_ID, CONTEXT);
        audit.events.clear();

        List<EvolutionEntryData> data = service.read(patient, USER_ID, CONTEXT);

        assertThat(data).extracting(EvolutionEntryData::id)
                .containsExactly(correction.id(), second.id(), first.id());
        assertThat(data).extracting(EvolutionEntryData::correctedByEntryId)
                .containsExactly(null, null, correction.id());
        assertThat(audit.events).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
            assertThat(event.entityId()).isNull();
        });
    }

    @Test
    void laLecturaDeUnPacienteInactivoEsPosibleYUnInexistenteDa404SinAuditoria() {
        assertThat(service.read(inactivePatient, USER_ID, CONTEXT)).isEmpty();
        assertThat(audit.events).hasSize(1);
        audit.events.clear();

        assertThatThrownBy(() -> service.read(UUID.randomUUID(), USER_ID, CONTEXT))
                .isInstanceOf(NotFoundException.class).hasMessage("PATIENT_NOT_FOUND");
        assertThat(audit.events).isEmpty();
    }

    private UUID appointment(UUID patientId, Instant startsAt, AppointmentStatusView status) {
        UUID id = UUID.randomUUID();
        appointments.put(id, new AppointmentNotificationView(
                id, patientId, "Limpieza", startsAt, startsAt.plusSeconds(1800), status));
        return id;
    }

    private static EvolutionCommand command(String description, UUID appointmentId) {
        return new EvolutionCommand(EvolutionDescription.of(description), appointmentId);
    }
}
