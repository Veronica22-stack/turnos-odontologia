package ar.turnosodontologia.consultorio.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.domain.Appointment;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentEventsPublisher;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentRepository;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus;
import ar.turnosodontologia.consultorio.agenda.domain.CancelledBy;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHours;
import ar.turnosodontologia.consultorio.agenda.domain.WorkingHoursRepository;
import ar.turnosodontologia.consultorio.pacientes.api.PatientLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientStatus;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.InvalidRangeException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.PracticeLock;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID OTHER_PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000009");
    private static final UUID PATIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID SERVICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID APPOINTMENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 2026-12-01 12:00Z; el 2026-12-10 es jueves (weekday 4) y Buenos Aires es UTC-3. */
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int THURSDAY = 4;

    @Mock
    private AppointmentRepository appointments;
    @Mock
    private PracticeServiceRepository services;
    @Mock
    private WorkingHoursRepository workingHours;
    @Mock
    private ScheduleBlockRepository blocks;
    @Mock
    private PatientLookup patients;
    @Mock
    private PracticeLock lock;
    @Mock
    private AppointmentEventsPublisher events;
    @Mock
    private InstallationPractice practice;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        lenient().when(practice.id()).thenReturn(PRACTICE_ID);
        lenient().when(practice.zone()).thenReturn(ZONE);
        service = new AppointmentService(appointments, services, workingHours, blocks, patients, lock, events,
                practice, CLOCK);
    }

    // ---- helpers ----

    /** Instante UTC de una hora local de Buenos Aires del 2026-12-10 (jueves). */
    private static Instant local(String time) {
        return Instant.parse("2026-12-10T" + time + ":00-03:00");
    }

    private static WorkingHours hours(String start, String end) {
        return WorkingHours.create(PRACTICE_ID, THURSDAY, LocalTime.parse(start), LocalTime.parse(end));
    }

    private static PracticeService catalogService(int durationMin, boolean active, boolean publicVisible) {
        return PracticeService.existing(SERVICE_ID, PRACTICE_ID, "Limpieza", null, durationMin, active,
                publicVisible, NOW, NOW);
    }

    private void givenActivePatient() {
        when(patients.findStatus(PATIENT_ID)).thenReturn(Optional.of(new PatientStatus(PATIENT_ID, true)));
    }

    private void givenService(int durationMin) {
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService(durationMin, true, true)));
    }

    private void givenWorkingHours(WorkingHours... slots) {
        lenient().when(workingHours.findByPracticeId(PRACTICE_ID)).thenReturn(List.of(slots));
    }

    private void givenNoBlocksNorAppointments() {
        lenient().when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of());
        lenient().when(appointments.findOverlappingActive(any(), any(), any(), any())).thenReturn(List.of());
    }

    private void givenSaveReturnsArgument() {
        when(appointments.saveAndFlush(any(Appointment.class))).thenAnswer(call -> call.getArgument(0));
    }

    private BookAppointmentCommand command(Instant startsAt, boolean overbooking) {
        return new BookAppointmentCommand(PATIENT_ID, SERVICE_ID, startsAt, overbooking, null);
    }

    private static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            return error.code();
        }
        throw new AssertionError("se esperaba una DomainException");
    }

    // ---- alta (6.1) ----

    @Test
    void altaFelizCreaTurnoConfirmadoInternoConDuracionSnapshotDeLaPrestacion() {
        givenActivePatient();
        givenService(45);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.book(command(local("10:00"), false));

        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(result.origin()).isEqualTo(AppointmentOrigin.INTERNAL);
        assertThat(result.patientId()).isEqualTo(PATIENT_ID);
        assertThat(result.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(result.startsAt()).isEqualTo(local("10:00"));
        assertThat(result.endsAt()).isEqualTo(local("10:45"));
        assertThat(result.durationMin()).isEqualTo(45);
        assertThat(result.overbooking()).isFalse();
        ArgumentCaptor<Appointment> saved = ArgumentCaptor.forClass(Appointment.class);
        verify(appointments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
    }

    @Test
    void altaConPrestacionActivaNoPublicaEsAceptada() {
        givenActivePatient();
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService(30, true, false)));
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        assertThat(service.book(command(local("10:00"), false)).status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void pacienteInexistenteEsNotFoundYNoTomaElLock() {
        when(patients.findStatus(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(NotFoundException.class).hasMessage("PATIENT_NOT_FOUND");
        verifyNoInteractions(lock);
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void pacienteInactivoEsConflictYNoTomaElLock() {
        when(patients.findStatus(PATIENT_ID)).thenReturn(Optional.of(new PatientStatus(PATIENT_ID, false)));

        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(ConflictException.class).hasMessage("PATIENT_INACTIVE");
        verifyNoInteractions(lock);
    }

    @Test
    void prestacionInexistenteODeOtroConsultorioEsNotFound() {
        givenActivePatient();
        when(services.findById(SERVICE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(NotFoundException.class).hasMessage("SERVICE_NOT_FOUND");

        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(PracticeService.existing(SERVICE_ID,
                OTHER_PRACTICE_ID, "Ajena", null, 30, true, true, NOW, NOW)));
        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(NotFoundException.class).hasMessage("SERVICE_NOT_FOUND");
        verifyNoInteractions(lock);
    }

    @Test
    void prestacionInactivaEsReglaDeNegocioYNoTomaElLock() {
        givenActivePatient();
        when(services.findById(SERVICE_ID)).thenReturn(Optional.of(catalogService(30, false, true)));

        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(BusinessRuleException.class).hasMessage("SERVICE_INACTIVE");
        verifyNoInteractions(lock);
    }

    @Test
    void inicioPasadoOIgualAlActualEsRechazadoAntesDeConsultarPacienteYSinLock() {
        assertThat(codeOf(() -> service.book(command(NOW, false)))).isEqualTo("APPOINTMENT_IN_PAST");
        assertThat(codeOf(() -> service.book(command(NOW.minusSeconds(1), false)))).isEqualTo("APPOINTMENT_IN_PAST");

        verifyNoInteractions(patients, services, lock);
    }

    // ---- horario, sobreturno, bloqueos y turnos (6.3) ----

    private static ScheduleBlock block(String start, String end) {
        return ScheduleBlock.existing(UUID.randomUUID(), PRACTICE_ID, local(start), local(end), "Motivo privado",
                NOW, NOW);
    }

    private static Appointment existingAppointment(String start, int minutes) {
        Clock past = Clock.fixed(NOW.minus(Duration.ofDays(1)), ZoneOffset.UTC);
        return Appointment.create(PRACTICE_ID, UUID.randomUUID(), SERVICE_ID, local(start), minutes,
                AppointmentOrigin.INTERNAL, false, null, past);
    }

    private void givenValidReferences(int durationMin) {
        givenActivePatient();
        givenService(durationMin);
    }

    @Test
    void ordenDeValidacionesElLockPrecedeAHorarioBloqueosYTurnos() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        service.book(command(local("10:00"), false));

        InOrder order = inOrder(patients, services, lock, workingHours, blocks, appointments);
        order.verify(patients).findStatus(PATIENT_ID);
        order.verify(services).findById(SERVICE_ID);
        order.verify(lock).lock(PRACTICE_ID);
        order.verify(workingHours).findByPracticeId(PRACTICE_ID);
        order.verify(blocks).findOverlapping(any(), any(), any());
        order.verify(appointments).findOverlappingActive(any(), any(), any(), any());
        order.verify(appointments).saveAndFlush(any(Appointment.class));
    }

    @Test
    void fueraDeHorarioSinSobreturnoEsReglaDeNegocio() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));

        assertThat(codeOf(() -> service.book(command(local("12:45"), false)))).isEqualTo("OUTSIDE_WORKING_HOURS");
        assertThat(codeOf(() -> service.book(command(local("08:30"), false)))).isEqualTo("OUTSIDE_WORKING_HOURS");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void diaSinTramosEsFueraDeHorario() {
        givenValidReferences(30);
        givenWorkingHours();

        assertThat(codeOf(() -> service.book(command(local("10:00"), false)))).isEqualTo("OUTSIDE_WORKING_HOURS");
    }

    @Test
    void turnoQueCruzaUnHuecoEntreTramosEsFueraDeHorario() {
        givenValidReferences(60);
        givenWorkingHours(hours("09:00", "12:00"), hours("13:00", "15:00"));

        assertThat(codeOf(() -> service.book(command(local("11:30"), false)))).isEqualTo("OUTSIDE_WORKING_HOURS");
    }

    @Test
    void turnoQueAtraviesaTramosContiguosEsAceptadoPasandoTodosLosTramosDelDia() {
        givenValidReferences(60);
        givenWorkingHours(hours("12:00", "15:00"), hours("09:00", "12:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.book(command(local("11:30"), false));

        assertThat(result.startsAt()).isEqualTo(local("11:30"));
        assertThat(result.endsAt()).isEqualTo(local("12:30"));
    }

    @Test
    void sobreturnoFueraDeHorarioEsAceptadoYSeGuardaElFlag() {
        givenValidReferences(30);
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.book(command(local("19:00"), true));

        assertThat(result.overbooking()).isTrue();
        verify(workingHours, never()).findByPracticeId(any());
    }

    @Test
    void turnoDentroDeUnBloqueoEsRechazadoAunComoSobreturno() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of(block("09:00", "11:00")));

        assertThat(codeOf(() -> service.book(command(local("10:30"), false)))).isEqualTo("APPOINTMENT_SLOT_BLOCKED");
        assertThat(codeOf(() -> service.book(command(local("10:30"), true)))).isEqualTo("APPOINTMENT_SLOT_BLOCKED");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void turnoAdyacenteAUnBloqueoEsAceptado() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of(block("09:00", "11:00")));
        lenient().when(appointments.findOverlappingActive(any(), any(), any(), any())).thenReturn(List.of());
        givenSaveReturnsArgument();

        assertThat(service.book(command(local("11:00"), false)).startsAt()).isEqualTo(local("11:00"));
    }

    @Test
    void turnoSolapadoConOtroVigenteEsConflictAunComoSobreturno() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of());
        when(appointments.findOverlappingActive(any(), any(), any(), any()))
                .thenReturn(List.of(existingAppointment("10:00", 30)));

        assertThatThrownBy(() -> service.book(command(local("10:15"), false)))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_TAKEN");
        assertThatThrownBy(() -> service.book(command(local("10:15"), true)))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_TAKEN");
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void turnoAdyacenteAOtroVigenteEsAceptado() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        when(appointments.findOverlappingActive(any(), any(), any(), any()))
                .thenReturn(List.of(existingAppointment("10:00", 30)));
        givenSaveReturnsArgument();

        assertThat(service.book(command(local("10:30"), false)).startsAt()).isEqualTo(local("10:30"));
    }

    @Test
    void noAplicaAnticipacionMinimaHorizonteMaximoNiGranularidad() {
        // Pocos minutos de anticipacion: ahora es 09:00 locales del martes 2026-12-01; turno 09:30 del mismo dia.
        givenValidReferences(30);
        when(workingHours.findByPracticeId(PRACTICE_ID)).thenReturn(List.of(
                WorkingHours.create(PRACTICE_ID, 2, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                hours("09:00", "13:00")));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        Instant soon = Instant.parse("2026-12-01T12:30:00Z");
        assertThat(service.book(command(soon, false)).startsAt()).isEqualTo(soon);
        // Hora no alineada a la granularidad (10:07) y a 9 dias de distancia.
        assertThat(service.book(command(local("10:07"), false)).startsAt()).isEqualTo(local("10:07"));
    }

    // ---- traduccion de la violacion de exclusion (6.5) ----

    @Test
    void laViolacionDeExclusionTrasElFlushSeTraduceASlotTaken() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(new DataIntegrityViolationException(
                "no revelar", new SQLException("conflicting key value violates exclusion constraint "
                        + "\"appointment_no_overlap\"", "23P01")));

        assertThatThrownBy(() -> service.book(command(local("10:00"), false)))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_TAKEN");
    }

    @Test
    void otraViolacionDeIntegridadSePropagaSinTraducir() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        DataIntegrityViolationException other = new DataIntegrityViolationException("otra",
                new SQLException("duplicate key value violates unique constraint \"otra\"", "23505"));
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(other);

        assertThatThrownBy(() -> service.book(command(local("10:00"), false))).isSameAs(other);
    }

    @Test
    void unaExclusionDeOtraTablaNoSeTraduceASlotTaken() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        DataIntegrityViolationException other = new DataIntegrityViolationException("otra",
                new SQLException("violates exclusion constraint \"schedule_block_no_overlap\"", "23P01"));
        when(appointments.saveAndFlush(any(Appointment.class))).thenThrow(other);

        assertThatThrownBy(() -> service.book(command(local("10:00"), false))).isSameAs(other);
    }

    // ---- cancelar, resultado y reprogramar (6.7) ----

    private Appointment storedAppointment(String start, int minutes) {
        Appointment appointment = existingAppointment(start, minutes);
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    private Appointment storedStartedAppointment(Instant startsAt) {
        Clock creation = Clock.fixed(startsAt.minus(Duration.ofDays(2)), ZoneOffset.UTC);
        Appointment appointment = Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, startsAt, 30,
                AppointmentOrigin.INTERNAL, false, null, creation);
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        return appointment;
    }

    @Test
    void cancelaUnTurnoConfirmadoComoDentistaConElInstanteDelServidor() {
        Appointment stored = storedAppointment("10:00", 30);

        AppointmentResult result = service.cancel(APPOINTMENT_ID);

        assertThat(result.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(result.cancelledBy()).isEqualTo(CancelledBy.DENTIST);
        assertThat(result.cancelledAt()).isEqualTo(NOW);
        assertThat(stored.status()).isEqualTo(AppointmentStatus.CANCELLED);
        verify(lock).lock(PRACTICE_ID);
    }

    @Test
    void cancelarUnTurnoTerminalEsTransicionInvalida() {
        Appointment stored = storedStartedAppointment(NOW.minus(Duration.ofHours(1)));
        stored.complete(CLOCK);

        assertThatThrownBy(() -> service.cancel(APPOINTMENT_ID))
                .isInstanceOf(ConflictException.class).hasMessage("INVALID_STATUS_TRANSITION");
        assertThat(stored.status()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void completaYMarcaAusenteUnTurnoYaIniciado() {
        storedStartedAppointment(NOW.minus(Duration.ofHours(1)));
        assertThat(service.complete(APPOINTMENT_ID).status()).isEqualTo(AppointmentStatus.COMPLETED);

        storedStartedAppointment(NOW.minus(Duration.ofMinutes(10)));
        assertThat(service.noShow(APPOINTMENT_ID).status()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void marcarResultadoAntesDelInicioEs422YElTurnoSigueConfirmado() {
        Appointment stored = storedAppointment("10:00", 30);

        assertThat(codeOf(() -> service.complete(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_STARTED");
        assertThat(codeOf(() -> service.noShow(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_STARTED");
        assertThat(stored.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void unTurnoInexistenteODeOtroConsultorioEsAppointmentNotFoundEnTodasLasOperaciones() {
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.empty());
        assertThat(codeOf(() -> service.cancel(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(codeOf(() -> service.complete(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(codeOf(() -> service.noShow(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(codeOf(() -> service.get(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("15:00"), false)))
                .isEqualTo("APPOINTMENT_NOT_FOUND");

        Clock past = Clock.fixed(NOW.minus(Duration.ofDays(1)), ZoneOffset.UTC);
        Appointment foreign = Appointment.create(OTHER_PRACTICE_ID, PATIENT_ID, SERVICE_ID, local("10:00"), 30,
                AppointmentOrigin.INTERNAL, false, null, past);
        when(appointments.findById(APPOINTMENT_ID)).thenReturn(Optional.of(foreign));
        assertThat(codeOf(() -> service.cancel(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(codeOf(() -> service.get(APPOINTMENT_ID))).isEqualTo("APPOINTMENT_NOT_FOUND");
        assertThat(foreign.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void reprogramaInPlaceExcluyendoSuPropioIdYConservandoLaDuracionSnapshot() {
        Appointment stored = storedAppointment("10:00", 45);
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.reschedule(APPOINTMENT_ID, local("15:00"), false);

        assertThat(result.id()).isEqualTo(stored.id());
        assertThat(result.startsAt()).isEqualTo(local("15:00"));
        assertThat(result.endsAt()).isEqualTo(local("15:45"));
        assertThat(result.durationMin()).isEqualTo(45);
        assertThat(result.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        verify(lock).lock(PRACTICE_ID);
        verify(appointments).findOverlappingActive(PRACTICE_ID, local("15:00"), local("15:45"), stored.id());
        verifyNoInteractions(services, patients);
    }

    @Test
    void reprogramarAUnRangoSolapadoConElPropioRangoAnteriorEsAceptado() {
        storedAppointment("10:00", 30);
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.reschedule(APPOINTMENT_ID, local("10:15"), false);

        assertThat(result.startsAt()).isEqualTo(local("10:15"));
        assertThat(result.endsAt()).isEqualTo(local("10:45"));
    }

    @Test
    void reprogramarContraOtroTurnoEsConflictYElOriginalQuedaIntacto() {
        Appointment stored = storedAppointment("10:00", 30);
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        when(appointments.findOverlappingActive(any(), any(), any(), any()))
                .thenReturn(List.of(existingAppointment("15:00", 30)));

        assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, local("15:00"), false))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_TAKEN");
        assertThat(stored.startsAt()).isEqualTo(local("10:00"));
        assertThat(stored.endsAt()).isEqualTo(local("10:30"));
        verify(appointments, never()).saveAndFlush(any());
    }

    @Test
    void reprogramarDentroDeUnBloqueoOFueraDeHorarioSinSobreturnoSeRechaza() {
        Appointment stored = storedAppointment("10:00", 30);
        givenWorkingHours(hours("09:00", "17:00"));
        when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of(block("14:00", "16:00")));

        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("15:00"), false)))
                .isEqualTo("APPOINTMENT_SLOT_BLOCKED");
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("18:00"), false)))
                .isEqualTo("OUTSIDE_WORKING_HOURS");
        assertThat(stored.startsAt()).isEqualTo(local("10:00"));
    }

    @Test
    void reprogramarNoReleeLaPrestacionAunqueEsteInactivaOCambieDeDuracion() {
        storedAppointment("10:00", 45);
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        AppointmentResult result = service.reschedule(APPOINTMENT_ID, local("15:00"), false);

        assertThat(result.durationMin()).isEqualTo(45);
        verifyNoInteractions(services);
    }

    @Test
    void reprogramarRechazaElInicioPasadoAntesDelLockYValidaElEstadoDelTurnoBajoElLock() {
        // El nuevo inicio pasado no depende de datos concurrentes: falla antes del lock y sin cargar el turno.
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, NOW, false))).isEqualTo("APPOINTMENT_IN_PAST");
        verifyNoInteractions(lock, appointments);

        // El estado del turno se valida despues del lock (desvio aprobado D3/D18).
        storedStartedAppointment(NOW.minusSeconds(60));
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("15:00"), false)))
                .isEqualTo("APPOINTMENT_ALREADY_STARTED");

        Appointment cancelled = storedAppointment("11:00", 30);
        cancelled.cancel(CancelledBy.PATIENT, CLOCK);
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("15:00"), false)))
                .isEqualTo("INVALID_STATUS_TRANSITION");
        verify(lock, times(2)).lock(PRACTICE_ID);
    }

    // ---- lock antes de leer el turno en toda mutacion (12.1) ----

    @Test
    void cancelarCompletarMarcarAusenteYReprogramarTomanElLockAntesDeLeerElTurno() {
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        storedAppointment("10:00", 30);
        service.cancel(APPOINTMENT_ID);
        storedStartedAppointment(NOW.minus(Duration.ofHours(1)));
        service.complete(APPOINTMENT_ID);
        storedStartedAppointment(NOW.minus(Duration.ofHours(2)));
        service.noShow(APPOINTMENT_ID);
        storedAppointment("10:00", 30);
        service.reschedule(APPOINTMENT_ID, local("15:00"), false);

        InOrder order = inOrder(lock, appointments);
        for (int i = 0; i < 4; i++) {
            order.verify(lock).lock(PRACTICE_ID);
            order.verify(appointments).findById(APPOINTMENT_ID);
        }
    }

    @Test
    void elTurnoSeCargaUnaSolaVezPorMutacion() {
        storedAppointment("10:00", 30);

        service.cancel(APPOINTMENT_ID);

        verify(appointments, times(1)).findById(APPOINTMENT_ID);
    }

    @Test
    void consultaYListadoNoTomanElLock() {
        Appointment stored = storedAppointment("10:00", 30);
        when(appointments.findInRange(PRACTICE_ID, local("09:00"), local("12:00"))).thenReturn(List.of(stored));

        service.get(APPOINTMENT_ID);
        service.list(local("09:00"), local("12:00"));

        verifyNoInteractions(lock);
    }

    @Test
    void consultaPorIdYListadoPorRangoDevuelvenResultados() {
        Appointment stored = storedAppointment("10:00", 30);
        when(appointments.findInRange(PRACTICE_ID, local("09:00"), local("12:00"))).thenReturn(List.of(stored));

        assertThat(service.get(APPOINTMENT_ID).patientId()).isEqualTo(stored.patientId());
        assertThat(service.list(local("09:00"), local("12:00"))).hasSize(1);
        assertThatThrownBy(() -> service.list(local("12:00"), local("12:00")))
                .isInstanceOf(InvalidRangeException.class);
        assertThatThrownBy(() -> service.list(null, local("12:00"))).isInstanceOf(InvalidRangeException.class);
        assertThatThrownBy(() -> service.list(local("12:00"), local("09:00")))
                .isInstanceOf(InvalidRangeException.class);
    }

    // ---- eventos (6.9) ----

    @Test
    void altaExitosaPublicaEventoConfirmadoConElTurnoGuardado() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        service.book(command(local("10:00"), false));

        ArgumentCaptor<Appointment> published = ArgumentCaptor.forClass(Appointment.class);
        verify(events).confirmed(published.capture());
        assertThat(published.getValue().startsAt()).isEqualTo(local("10:00"));
        assertThat(published.getValue().origin()).isEqualTo(AppointmentOrigin.INTERNAL);
    }

    @Test
    void cancelarPublicaEventoCancelado() {
        Appointment stored = storedAppointment("10:00", 30);

        service.cancel(APPOINTMENT_ID);

        verify(events).cancelled(stored);
        assertThat(stored.cancelledBy()).isEqualTo(CancelledBy.DENTIST);
    }

    @Test
    void reprogramarPublicaEventoConLosInstantesAnterioresYNuevos() {
        Appointment stored = storedAppointment("10:00", 30);
        givenWorkingHours(hours("09:00", "17:00"));
        givenNoBlocksNorAppointments();
        givenSaveReturnsArgument();

        service.reschedule(APPOINTMENT_ID, local("15:00"), false);

        verify(events).rescheduled(stored, local("10:00"), local("10:30"));
        assertThat(stored.startsAt()).isEqualTo(local("15:00"));
    }

    @Test
    void unRechazoNoPublicaNingunEvento() {
        givenValidReferences(30);
        givenWorkingHours(hours("09:00", "13:00"));
        when(blocks.findOverlapping(any(), any(), any())).thenReturn(List.of(block("09:00", "11:00")));
        assertThat(codeOf(() -> service.book(command(local("10:00"), false)))).isEqualTo("APPOINTMENT_SLOT_BLOCKED");

        Appointment stored = storedAppointment("10:00", 30);
        assertThat(codeOf(() -> service.reschedule(APPOINTMENT_ID, local("10:30"), false)))
                .isEqualTo("APPOINTMENT_SLOT_BLOCKED");
        assertThat(stored.startsAt()).isEqualTo(local("10:00"));

        verifyNoInteractions(events);
    }

    @Test
    void completarYMarcarAusenteNoPublicanEventos() {
        storedStartedAppointment(NOW.minus(Duration.ofHours(1)));
        service.complete(APPOINTMENT_ID);
        storedStartedAppointment(NOW.minus(Duration.ofHours(2)));
        service.noShow(APPOINTMENT_ID);

        verifyNoInteractions(events);
    }

    // ---- ajustes post-verify (13.1): tope de 93 dias y nulos en reschedule ----

    @Test
    void elListadoAceptaExactamente93DiasYRechazaMasDe93DiasPorUnNanosegundo() {
        Instant from = local("09:00");
        when(appointments.findInRange(PRACTICE_ID, from, from.plus(Duration.ofDays(93)))).thenReturn(List.of());

        assertThat(service.list(from, from.plus(Duration.ofDays(93)))).isEmpty();
        assertThat(AppointmentService.MAX_LIST_RANGE).isEqualTo(Duration.ofDays(93));
        assertThatThrownBy(() -> service.list(from, from.plus(Duration.ofDays(93)).plusNanos(1)))
                .isInstanceOf(InvalidRangeException.class).hasMessage("INVALID_RANGE");
        assertThatThrownBy(() -> service.list(from, from.plus(Duration.ofDays(94))))
                .isInstanceOf(InvalidRangeException.class).hasMessage("INVALID_RANGE");
        verify(appointments, times(1)).findInRange(any(), any(), any());
    }

    @Test
    void elListadoInvalidoSigueDandoInvalidRangeConLaExcepcionExplicita() {
        assertThatThrownBy(() -> service.list(null, local("12:00"))).isInstanceOf(InvalidRangeException.class);
        assertThatThrownBy(() -> service.list(local("09:00"), null)).isInstanceOf(InvalidRangeException.class);
        assertThatThrownBy(() -> service.list(local("12:00"), local("12:00")))
                .isInstanceOf(InvalidRangeException.class);
        assertThatThrownBy(() -> service.list(local("12:00"), local("09:00")))
                .isInstanceOf(InvalidRangeException.class);
        verifyNoInteractions(appointments);
    }

    @Test
    void reprogramarSinNuevoInicioOSinIdEsErrorDeValidacionYNoUnNullPointer() {
        assertThatThrownBy(() -> service.reschedule(APPOINTMENT_ID, null, false))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("newStartsAt"));
        assertThatThrownBy(() -> service.reschedule(null, local("12:00"), false))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("appointmentId"));
        verifyNoInteractions(lock, appointments);
    }
}
