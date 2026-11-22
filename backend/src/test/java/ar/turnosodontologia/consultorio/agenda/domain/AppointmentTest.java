package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class AppointmentTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID PATIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID SERVICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant FUTURE = Instant.parse("2026-12-10T13:00:00Z");

    private static Appointment confirmed(Instant startsAt, int durationMin) {
        // Se crea con un reloj anterior para poder construir turnos "ya iniciados" respecto de CLOCK.
        Clock creation = Clock.fixed(startsAt.minus(Duration.ofDays(30)), ZoneOffset.UTC);
        return Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, startsAt, durationMin,
                AppointmentOrigin.INTERNAL, false, null, creation);
    }

    // ---- creacion (3.1) ----

    @Test
    void creaTurnoConfirmadoConFinCalculadoConLaDuracionSnapshot() {
        Appointment appointment = Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, FUTURE, 45,
                AppointmentOrigin.INTERNAL, false, "nota", CLOCK);

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.origin()).isEqualTo(AppointmentOrigin.INTERNAL);
        assertThat(appointment.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(appointment.patientId()).isEqualTo(PATIENT_ID);
        assertThat(appointment.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(appointment.startsAt()).isEqualTo(FUTURE);
        assertThat(appointment.endsAt()).isEqualTo(FUTURE.plus(Duration.ofMinutes(45)));
        assertThat(appointment.durationMinSnapshot()).isEqualTo(45);
        assertThat(appointment.overbooking()).isFalse();
        assertThat(appointment.notes()).isEqualTo("nota");
        assertThat(appointment.cancelledBy()).isNull();
        assertThat(appointment.cancelledAt()).isNull();
        assertThat(appointment.createdAt()).isEqualTo(NOW);
        assertThat(appointment.updatedAt()).isEqualTo(NOW);
        assertThat(appointment.id()).isNull();
    }

    @Test
    void conservaElSobreturnoYElOrigen() {
        Appointment appointment = Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, FUTURE, 30,
                AppointmentOrigin.PUBLIC, true, null, CLOCK);

        assertThat(appointment.overbooking()).isTrue();
        assertThat(appointment.origin()).isEqualTo(AppointmentOrigin.PUBLIC);
    }

    @Test
    void elInicioDebeSerEstrictamentePosteriorAlInstanteActual() {
        assertThat(codeOf(() -> Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, NOW, 30,
                AppointmentOrigin.INTERNAL, false, null, CLOCK))).isEqualTo("APPOINTMENT_IN_PAST");
        assertThat(codeOf(() -> Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, NOW.minusSeconds(1), 30,
                AppointmentOrigin.INTERNAL, false, null, CLOCK))).isEqualTo("APPOINTMENT_IN_PAST");
        assertThat(Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, NOW.plusMillis(1), 30,
                AppointmentOrigin.INTERNAL, false, null, CLOCK).startsAt()).isEqualTo(NOW.plusMillis(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    void laDuracionSnapshotDebeSerPositiva(int duration) {
        assertThatThrownBy(() -> Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, FUTURE, duration,
                AppointmentOrigin.INTERNAL, false, null, CLOCK)).isInstanceOf(ValidationException.class);
    }

    @Test
    void rechazaNotasDeMasDe1000CaracteresYAceptaExactamente1000() {
        assertThat(Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, FUTURE, 30,
                AppointmentOrigin.INTERNAL, false, "x".repeat(1000), CLOCK).notes()).hasSize(1000);
        assertThatThrownBy(() -> Appointment.create(PRACTICE_ID, PATIENT_ID, SERVICE_ID, FUTURE, 30,
                AppointmentOrigin.INTERNAL, false, "x".repeat(1001), CLOCK))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void idSeGeneraComoUuidVersion7() throws NoSuchFieldException {
        Field idField = Appointment.class.getDeclaredField("id");
        UuidGenerator generator = idField.getAnnotation(UuidGenerator.class);

        assertThat(generator).isNotNull();
        assertThat(generator.style()).isEqualTo(UuidGenerator.Style.VERSION_7);
    }

    // ---- transiciones (3.3) ----

    @ParameterizedTest
    @EnumSource(CancelledBy.class)
    void cancelaUnTurnoConfirmadoFuturoRegistrandoQuienYCuando(CancelledBy by) {
        Appointment appointment = confirmed(FUTURE, 30);

        appointment.cancel(by, CLOCK);

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.cancelledBy()).isEqualTo(by);
        assertThat(appointment.cancelledAt()).isEqualTo(NOW);
        assertThat(appointment.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void cancelaUnTurnoConfirmadoYaIniciado() {
        Appointment appointment = confirmed(NOW.minus(Duration.ofHours(2)), 30);

        appointment.cancel(CancelledBy.DENTIST, CLOCK);

        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void completaYMarcaAusenteUnTurnoYaIniciadoIncluidoElInstanteExacto() {
        Appointment started = confirmed(NOW.minus(Duration.ofHours(1)), 30);
        Appointment exactlyNow = confirmed(NOW, 30);
        Appointment absent = confirmed(NOW.minus(Duration.ofMinutes(5)), 30);

        started.complete(CLOCK);
        exactlyNow.complete(CLOCK);
        absent.markNoShow(CLOCK);

        assertThat(started.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(exactlyNow.status()).isEqualTo(AppointmentStatus.COMPLETED);
        assertThat(absent.status()).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(started.updatedAt()).isEqualTo(NOW);
        assertThat(started.cancelledBy()).isNull();
    }

    @Test
    void noSePuedeMarcarResultadoAntesDelInicio() {
        Appointment completeAttempt = confirmed(NOW.plusSeconds(1), 30);
        Appointment noShowAttempt = confirmed(FUTURE, 30);

        assertThat(codeOf(() -> completeAttempt.complete(CLOCK))).isEqualTo("APPOINTMENT_NOT_STARTED");
        assertThat(codeOf(() -> noShowAttempt.markNoShow(CLOCK))).isEqualTo("APPOINTMENT_NOT_STARTED");
        assertThat(completeAttempt.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(noShowAttempt.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThatThrownBy(() -> completeAttempt.complete(CLOCK)).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "COMPLETED", "NO_SHOW"})
    void todaAccionSobreUnEstadoTerminalEsTransicionInvalida(AppointmentStatus terminal) {
        Appointment appointment = terminalAppointment(terminal);

        assertThatThrownBy(() -> appointment.cancel(CancelledBy.DENTIST, CLOCK))
                .isInstanceOf(ConflictException.class).hasMessage("INVALID_STATUS_TRANSITION");
        assertThatThrownBy(() -> appointment.complete(CLOCK))
                .isInstanceOf(ConflictException.class).hasMessage("INVALID_STATUS_TRANSITION");
        assertThatThrownBy(() -> appointment.markNoShow(CLOCK))
                .isInstanceOf(ConflictException.class).hasMessage("INVALID_STATUS_TRANSITION");
        assertThat(appointment.status()).isEqualTo(terminal);
    }

    // ---- reprogramacion (3.5) ----

    @Test
    void reprogramaConservandoIdSnapshotYActualizandoFinYSobreturno() {
        Appointment appointment = confirmed(FUTURE, 45);
        Instant newStart = Instant.parse("2026-12-11T15:00:00Z");

        appointment.reschedule(newStart, true, CLOCK);

        assertThat(appointment.startsAt()).isEqualTo(newStart);
        assertThat(appointment.endsAt()).isEqualTo(newStart.plus(Duration.ofMinutes(45)));
        assertThat(appointment.durationMinSnapshot()).isEqualTo(45);
        assertThat(appointment.overbooking()).isTrue();
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.patientId()).isEqualTo(PATIENT_ID);
        assertThat(appointment.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(appointment.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void reprogramarAUnRangoQueSolapaConElAnteriorEsValidoEnElDominio() {
        Appointment appointment = confirmed(FUTURE, 30);

        appointment.reschedule(FUTURE.plus(Duration.ofMinutes(15)), false, CLOCK);

        assertThat(appointment.startsAt()).isEqualTo(FUTURE.plus(Duration.ofMinutes(15)));
        assertThat(appointment.overbooking()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CANCELLED", "COMPLETED", "NO_SHOW"})
    void soloSeReprogramaUnTurnoConfirmado(AppointmentStatus terminal) {
        Appointment appointment = terminalAppointment(terminal);
        Instant before = appointment.startsAt();

        assertThatThrownBy(() -> appointment.reschedule(FUTURE, false, CLOCK))
                .isInstanceOf(ConflictException.class).hasMessage("INVALID_STATUS_TRANSITION");
        assertThat(appointment.startsAt()).isEqualTo(before);
    }

    @Test
    void unTurnoYaIniciadoNoSePuedeReprogramar() {
        Appointment started = confirmed(NOW, 30);
        Appointment earlier = confirmed(NOW.minusSeconds(60), 30);

        assertThat(codeOf(() -> started.reschedule(FUTURE, false, CLOCK))).isEqualTo("APPOINTMENT_ALREADY_STARTED");
        assertThat(codeOf(() -> earlier.reschedule(FUTURE, false, CLOCK))).isEqualTo("APPOINTMENT_ALREADY_STARTED");
        assertThat(started.startsAt()).isEqualTo(NOW);
    }

    @Test
    void elNuevoInicioDebeSerFuturo() {
        Appointment appointment = confirmed(FUTURE, 30);

        assertThat(codeOf(() -> appointment.reschedule(NOW, false, CLOCK))).isEqualTo("APPOINTMENT_IN_PAST");
        assertThat(codeOf(() -> appointment.reschedule(NOW.minusSeconds(1), false, CLOCK)))
                .isEqualTo("APPOINTMENT_IN_PAST");
        assertThat(appointment.startsAt()).isEqualTo(FUTURE);
    }

    @Test
    void validarReprogramacionNoModificaElTurnoYCalculaElFinConLaDuracionSnapshot() {
        Appointment appointment = confirmed(FUTURE, 45);
        Instant newStart = Instant.parse("2026-12-11T15:00:00Z");

        appointment.validateReschedulable(newStart, CLOCK);

        assertThat(appointment.startsAt()).isEqualTo(FUTURE);
        assertThat(appointment.endsAtIfStartedAt(newStart)).isEqualTo(newStart.plus(Duration.ofMinutes(45)));
        assertThat(codeOf(() -> appointment.validateReschedulable(NOW, CLOCK))).isEqualTo("APPOINTMENT_IN_PAST");
    }

    private static Appointment terminalAppointment(AppointmentStatus terminal) {
        Appointment appointment = confirmed(NOW.minus(Duration.ofHours(1)), 30);
        switch (terminal) {
            case CANCELLED -> appointment.cancel(CancelledBy.PATIENT, CLOCK);
            case COMPLETED -> appointment.complete(CLOCK);
            case NO_SHOW -> appointment.markNoShow(CLOCK);
            default -> throw new IllegalArgumentException();
        }
        return appointment;
    }

    private static String codeOf(Runnable action) {
        try {
            action.run();
        } catch (DomainException error) {
            return error.code();
        }
        throw new AssertionError("se esperaba una DomainException");
    }
}
