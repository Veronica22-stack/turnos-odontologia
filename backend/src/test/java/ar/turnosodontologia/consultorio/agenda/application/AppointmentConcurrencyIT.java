package ar.turnosodontologia.consultorio.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.PracticeLock;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import ar.turnosodontologia.consultorio.support.ClinicalFixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrencia real (design D13): transacciones reales sobre PostgreSQL embebido, sin {@code @Transactional} en el
 * test, dos hilos liberados por un latch de arranque y timeout acotado. Se asertan estados finales de la base.
 */
class AppointmentConcurrencyIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);
    private static final long TIMEOUT_SECONDS = 30;
    private static final int ITERATIONS = 12;

    private final AppointmentService appointments;
    private final ScheduleBlockService blocks;
    private final JdbcTemplate jdbc;
    private final PracticeLock practiceLock;
    private final TransactionTemplate transactions;
    private final InstallationPractice practice;
    private UUID patient;
    private UUID service;

    AppointmentConcurrencyIT(AppointmentService appointments, ScheduleBlockService blocks, JdbcTemplate jdbc,
            PracticeLock practiceLock, TransactionTemplate transactions, InstallationPractice practice) {
        this.appointments = appointments;
        this.blocks = blocks;
        this.jdbc = jdbc;
        this.practiceLock = practiceLock;
        this.transactions = transactions;
        this.practice = practice;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        service = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(DAY.getDayOfWeek().getValue(), "09:00", "13:00");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static Instant at(String time) {
        return DAY.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }

    /** Resultado de una operacion concurrente: valor o codigo de la DomainException. */
    private record Outcome<T>(T value, String code) {
        boolean succeeded() {
            return code == null;
        }
    }

    private static <T> Callable<Outcome<T>> guarded(CountDownLatch start, Callable<T> operation) {
        return () -> {
            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            try {
                return new Outcome<>(operation.call(), null);
            } catch (DomainException error) {
                return new Outcome<>(null, error.code());
            }
        };
    }

    @SafeVarargs
    private static <T> List<Outcome<T>> runConcurrently(Callable<T>... operations) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(operations.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome<T>>> futures = new ArrayList<>();
            for (Callable<T> operation : operations) {
                futures.add(executor.submit(guarded(start, operation)));
            }
            start.countDown();
            List<Outcome<T>> outcomes = new ArrayList<>();
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private AppointmentResult book(String start) {
        return appointments.book(new BookAppointmentCommand(patient, service, at(start), false, null));
    }

    private int activeAppointments() {
        return jdbc.queryForObject("SELECT count(*) FROM appointment WHERE status <> 'CANCELLED'", Integer.class);
    }

    // ---- turno contra turno (10.1) ----

    @Test
    void dosAltasDelMismoSlotConfirmanExactamenteUnaYLaOtraEsSlotTaken() throws Exception {
        List<Outcome<AppointmentResult>> outcomes = runConcurrently(() -> book("10:00"), () -> book("10:00"));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::code).containsExactly("APPOINTMENT_SLOT_TAKEN");
        assertThat(activeAppointments()).isEqualTo(1);
    }

    @Test
    void dosAltasParcialmenteSolapadasConfirmanSoloUna() throws Exception {
        List<Outcome<AppointmentResult>> outcomes = runConcurrently(() -> book("10:00"), () -> book("10:15"));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::code).containsExactly("APPOINTMENT_SLOT_TAKEN");
        assertThat(activeAppointments()).isEqualTo(1);
    }

    @Test
    void dosAltasDeSlotsAdyacentesConfirmanAmbas() throws Exception {
        List<Outcome<AppointmentResult>> outcomes = runConcurrently(() -> book("10:00"), () -> book("10:30"));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(activeAppointments()).isEqualTo(2);
    }

    @Test
    void dosReprogramacionesAlMismoRangoConfirmanSoloUna() throws Exception {
        UUID first = book("09:00").id();
        UUID second = book("09:30").id();

        List<Outcome<AppointmentResult>> outcomes = runConcurrently(
                () -> appointments.reschedule(first, at("11:00"), false),
                () -> appointments.reschedule(second, at("11:00"), false));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::code).containsExactly("APPOINTMENT_SLOT_TAKEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM appointment WHERE starts_at = ?::timestamptz",
                Integer.class, at("11:00").toString())).isEqualTo(1);
    }

    // ---- turno contra bloqueo (10.2) ----

    @Test
    void turnoYBloqueoQueSeIntersectanNuncaDejanUnTurnoDentroSinInformarlo() throws Exception {
        int bookedFirst = 0;
        int blockedFirst = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            jdbc.update("DELETE FROM schedule_block");
            ClinicalFixtures.deleteEvolutions();
            AppointmentFixtures.execute("DELETE FROM notification");
            AppointmentFixtures.execute("DELETE FROM appointment");

            List<Outcome<?>> outcomes = new ArrayList<>(runConcurrently(
                    () -> book("10:00"), () -> blocks.create(at("09:00"), at("12:00"), "IT-APPT")));
            @SuppressWarnings("unchecked")
            Outcome<AppointmentResult> booking = (Outcome<AppointmentResult>) outcomes.get(0);
            @SuppressWarnings("unchecked")
            Outcome<ScheduleBlockResult> block = (Outcome<ScheduleBlockResult>) outcomes.get(1);

            assertThat(block.succeeded()).as("el bloqueo siempre se crea").isTrue();
            if (booking.succeeded()) {
                bookedFirst++;
                assertThat(block.value().affectedAppointments())
                        .as("el turno confirmado antes del bloqueo debe figurar como afectado")
                        .extracting(slot -> slot.appointmentId()).contains(booking.value().id());
            } else {
                blockedFirst++;
                assertThat(booking.code()).isEqualTo("APPOINTMENT_SLOT_BLOCKED");
                assertThat(activeAppointments()).isZero();
            }
        }
        assertThat(bookedFirst + blockedFirst).isEqualTo(ITERATIONS);
    }

    @Test
    void operacionesSobreRangosDisjuntosConfirmanAmbas() throws Exception {
        List<Outcome<?>> outcomes = new ArrayList<>(runConcurrently(
                () -> book("10:00"), () -> blocks.create(at("15:00"), at("16:00"), "IT-APPT")));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(activeAppointments()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_block", Integer.class)).isEqualTo(1);
    }

    @Test
    void trasUnRechazoLasOperacionesSiguientesNoQuedanBloqueadas() throws Exception {
        book("10:00");
        List<Outcome<AppointmentResult>> rejected = runConcurrently(() -> book("10:00"));
        assertThat(rejected.get(0).code()).isEqualTo("APPOINTMENT_SLOT_TAKEN");

        List<Outcome<?>> next = new ArrayList<>(runConcurrently(
                () -> blocks.create(at("15:00"), at("16:00"), "IT-APPT"), () -> book("11:00")));

        assertThat(next).allMatch(Outcome::succeeded);
    }

    // ---- el lock por consultorio se toma de punta a punta (determinista) ----

    @Test
    void altaDeTurnoYAltaYEdicionDeBloqueoEsperanAlLockDelConsultorioPeroLaBajaNo() throws Exception {
        UUID practiceId = practice.id();
        UUID existingBlock = blocks.create(at("14:00"), at("15:00"), "IT-APPT").id();
        UUID deletableBlock = blocks.create(at("16:00"), at("17:00"), "IT-APPT").id();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                practiceLock.lock(practiceId);
                holding.countDown();
                awaitLatch(release);
            }));
            assertThat(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<AppointmentResult> booking = executor.submit(() -> book("10:00"));
            Future<ScheduleBlockResult> creating = executor.submit(() -> blocks.create(at("11:00"), at("12:00"), "IT-APPT"));
            Future<ScheduleBlockResult> editing = executor.submit(
                    () -> blocks.update(existingBlock, at("14:00"), at("15:30"), "IT-APPT"));
            Future<?> deleting = executor.submit(() -> blocks.delete(deletableBlock));

            // La baja de un bloqueo no requiere el lock: termina aunque otra transaccion lo retenga.
            deleting.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            for (Future<?> waiting : List.of(booking, creating, editing)) {
                try {
                    waiting.get(500, TimeUnit.MILLISECONDS);
                    throw new AssertionError("la operacion debia esperar al lock del consultorio");
                } catch (TimeoutException expected) {
                    // esperando al lock
                }
            }

            release.countDown();
            holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(booking.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status().name()).isEqualTo("CONFIRMED");
            assertThat(creating.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).id()).isNotNull();
            assertThat(editing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).endsAt()).isEqualTo(at("15:30"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout esperando el latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    // ---- lock por consultorio en toda mutacion de turno (12.2, desvio aprobado D3) ----

    /** Retiene el lock del consultorio en otra transaccion hasta {@code release}; devuelve cuando ya lo tiene. */
    private Future<?> holdPracticeLock(ExecutorService executor, CountDownLatch release) throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
            practiceLock.lock(practice.id());
            holding.countDown();
            awaitLatch(release);
        }));
        assertThat(holding.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        return holder;
    }

    private static void assertWaiting(Future<?> future) throws Exception {
        try {
            future.get(500, TimeUnit.MILLISECONDS);
            throw new AssertionError("la operacion debia esperar al lock del consultorio");
        } catch (TimeoutException expected) {
            // esperando al lock
        }
    }

    @Test
    void cancelarCompletarMarcarAusenteYReprogramarEsperanAlLockDelConsultorio() throws Exception {
        UUID toCancel = book("10:00").id();
        UUID toMove = book("11:00").id();
        UUID toComplete = AppointmentFixtures.appointment(patient, service, "2020-03-05T13:00:00Z",
                "2020-03-05T13:30:00Z", "CONFIRMED");
        UUID toNoShow = AppointmentFixtures.appointment(patient, service, "2020-03-05T14:00:00Z",
                "2020-03-05T14:30:00Z", "CONFIRMED");
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        try {
            Future<?> holder = holdPracticeLock(executor, release);

            Future<AppointmentResult> cancelling = executor.submit(() -> appointments.cancel(toCancel));
            Future<AppointmentResult> completing = executor.submit(() -> appointments.complete(toComplete));
            Future<AppointmentResult> noShowing = executor.submit(() -> appointments.noShow(toNoShow));
            Future<AppointmentResult> moving = executor.submit(
                    () -> appointments.reschedule(toMove, at("12:00"), false));

            for (Future<?> waiting : List.of(cancelling, completing, noShowing, moving)) {
                assertWaiting(waiting);
            }

            release.countDown();
            holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(cancelling.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status().name()).isEqualTo("CANCELLED");
            assertThat(completing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status().name()).isEqualTo("COMPLETED");
            assertThat(noShowing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).status().name()).isEqualTo("NO_SHOW");
            assertThat(moving.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).startsAt()).isEqualTo(at("12:00"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void reprogramarYCancelarElMismoTurnoNuncaPierdeLaCancelacion() throws Exception {
        UUID id = book("10:00").id();
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = holdPracticeLock(executor, release);

            // La reprogramacion arranca primero y queda esperando; ventana en la que antes cargaba el turno.
            Future<Outcome<AppointmentResult>> moving = executor.submit(guarded(new CountDownLatch(0),
                    () -> appointments.reschedule(id, at("12:00"), false)));
            try {
                moving.get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                // esperando al lock (o, sin el fix, ya habiendo leido el turno)
            }
            Future<Outcome<AppointmentResult>> cancelling = executor.submit(
                    guarded(new CountDownLatch(0), () -> appointments.cancel(id)));
            try {
                cancelling.get(500, TimeUnit.MILLISECONDS);
            } catch (TimeoutException expected) {
                // esperando al lock
            }

            release.countDown();
            holder.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Outcome<AppointmentResult> moved = moving.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Outcome<AppointmentResult> cancelled = cancelling.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String finalStatus = AppointmentFixtures.statusOf(id);
            assertThat(cancelled.succeeded()).as("la cancelacion siempre procede").isTrue();
            assertThat(finalStatus).as("una cancelacion confirmada nunca se revierte").isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL "
                    + "FROM appointment WHERE id = ?", Boolean.class, id)).isTrue();
            if (!moved.succeeded()) {
                assertThat(moved.code()).isEqualTo("INVALID_STATUS_TRANSITION");
            }
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void completarYReprogramarUnTurnoYaIniciadoDejaElEstadoTerminal() throws Exception {
        UUID id = AppointmentFixtures.appointment(patient, service, "2020-03-05T13:00:00Z",
                "2020-03-05T13:30:00Z", "CONFIRMED");

        List<Outcome<AppointmentResult>> outcomes = runConcurrently(
                () -> appointments.complete(id), () -> appointments.reschedule(id, at("12:00"), false));

        assertThat(outcomes.get(0).succeeded()).isTrue();
        assertThat(outcomes.get(1).code()).isIn("APPOINTMENT_ALREADY_STARTED", "INVALID_STATUS_TRANSITION");
        assertThat(AppointmentFixtures.statusOf(id)).isEqualTo("COMPLETED");
    }
}
