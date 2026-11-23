package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.notificaciones.domain.Notification;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationStatus;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** Upserts atomicos por (turno, tipo) y toma de filas con SKIP LOCKED (design D4, D5). */
class NotificationRepositoryIT extends AbstractIntegrationTest {

    private static final UUID PRACTICE = UUID.fromString(AppointmentFixtures.PRACTICE_ID);
    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");

    private final NotificationRepository repository;
    private final TransactionTemplate transactions;
    private UUID patient;
    private UUID service;
    private UUID appointment;

    NotificationRepositoryIT(NotificationRepository repository, TransactionTemplate transactions) {
        this.repository = repository;
        this.transactions = transactions;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        patient = AppointmentFixtures.patient(true);
        service = AppointmentFixtures.service(30, true, true);
        appointment = newAppointment("2026-12-10T13:00:00Z", "2026-12-10T13:30:00Z");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private UUID newAppointment(String from, String to) {
        return AppointmentFixtures.appointment(patient, service, from, to, "CONFIRMED");
    }

    private Optional<Notification> find(UUID appointmentId, NotificationType type) {
        return transactions.execute(status -> repository.findByAppointmentIdAndType(appointmentId, type));
    }

    private void enqueue(UUID appointmentId, NotificationType type, Instant scheduledFor) {
        transactions.executeWithoutResult(status ->
                repository.enqueueIfAbsent(PRACTICE, appointmentId, type, scheduledFor, NOW));
    }

    @Test
    void enqueueIfAbsentCreaUnaPendienteYNoCambiaUnaFilaExistente() {
        enqueue(appointment, NotificationType.CONFIRMATION, NOW);
        transactions.executeWithoutResult(status -> {
            Notification row = repository.findByAppointmentIdAndType(appointment, NotificationType.CONFIRMATION).orElseThrow();
            row.markSent(NOW.plusSeconds(1));
            repository.save(row);
        });

        enqueue(appointment, NotificationType.CONFIRMATION, NOW.plusSeconds(3600));

        Notification row = find(appointment, NotificationType.CONFIRMATION).orElseThrow();
        assertThat(row.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(row.scheduledFor()).isEqualTo(NOW);
        assertThat(row.sentAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void enqueueIfAbsentGuardaPracticaTurnoTipoFechaYDefaults() {
        enqueue(appointment, NotificationType.REMINDER, Instant.parse("2026-12-09T13:00:00Z"));

        Notification row = find(appointment, NotificationType.REMINDER).orElseThrow();
        assertThat(row.practiceId()).isEqualTo(PRACTICE);
        assertThat(row.appointmentId()).isEqualTo(appointment);
        assertThat(row.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.attempts()).isZero();
        assertThat(row.scheduledFor()).isEqualTo(Instant.parse("2026-12-09T13:00:00Z"));
    }

    @Test
    void upsertPendingReseteaUnaFilaEnviadaConElNuevoHorario() {
        enqueue(appointment, NotificationType.REMINDER, NOW);
        transactions.executeWithoutResult(status -> {
            Notification row = repository.findByAppointmentIdAndType(appointment, NotificationType.REMINDER).orElseThrow();
            row.markFailed(NotificationErrorCode.SMTP_REJECTED, NOW);
            repository.save(row);
        });

        Instant newTime = Instant.parse("2026-12-14T13:00:00Z");
        transactions.executeWithoutResult(status ->
                repository.upsertPending(PRACTICE, appointment, NotificationType.REMINDER, newTime, NOW));

        Notification row = find(appointment, NotificationType.REMINDER).orElseThrow();
        assertThat(row.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.attempts()).isZero();
        assertThat(row.sentAt()).isNull();
        assertThat(row.lastError()).isNull();
        assertThat(row.scheduledFor()).isEqualTo(newTime);
    }

    @Test
    void upsertPendingSobreUnaSentTambienLaVuelveAPendiente() {
        enqueue(appointment, NotificationType.RESCHEDULE, NOW);
        transactions.executeWithoutResult(status -> {
            Notification row = repository.findByAppointmentIdAndType(appointment, NotificationType.RESCHEDULE).orElseThrow();
            row.markSent(NOW);
            repository.save(row);
        });

        transactions.executeWithoutResult(status ->
                repository.upsertPending(PRACTICE, appointment, NotificationType.RESCHEDULE, NOW.plusSeconds(60), NOW));

        assertThat(find(appointment, NotificationType.RESCHEDULE).orElseThrow().status())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(count(appointment)).isEqualTo(1);
    }

    @Test
    void discardPendingForCancellationDescartaPendientesNoDeCancelacionDelTurno() {
        UUID other = newAppointment("2026-12-11T13:00:00Z", "2026-12-11T13:30:00Z");
        enqueue(appointment, NotificationType.CONFIRMATION, NOW);
        enqueue(appointment, NotificationType.REMINDER, NOW);
        enqueue(other, NotificationType.REMINDER, NOW);
        transactions.executeWithoutResult(status -> {
            Notification sent = repository.findByAppointmentIdAndType(appointment, NotificationType.CONFIRMATION).orElseThrow();
            sent.markSent(NOW);
            repository.save(sent);
        });

        int discarded = transactions.execute(status ->
                repository.discardPendingForCancellation(appointment, NotificationErrorCode.APPOINTMENT_CANCELLED, NOW));

        assertThat(discarded).isEqualTo(1);
        assertThat(find(appointment, NotificationType.CONFIRMATION).orElseThrow().status())
                .isEqualTo(NotificationStatus.SENT);
        Notification reminder = find(appointment, NotificationType.REMINDER).orElseThrow();
        assertThat(reminder.status()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(reminder.lastError()).isEqualTo("APPOINTMENT_CANCELLED");
        assertThat(find(other, NotificationType.REMINDER).orElseThrow().status()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void discardPendingForCancellationNoTocaLasNotificacionesDeCancelacion() {
        enqueue(appointment, NotificationType.CANCELLATION, NOW);
        enqueue(appointment, NotificationType.DENTIST_CANCELLATION, NOW);
        enqueue(appointment, NotificationType.RESCHEDULE, NOW);

        int discarded = transactions.execute(status ->
                repository.discardPendingForCancellation(appointment, NotificationErrorCode.APPOINTMENT_CANCELLED, NOW));

        assertThat(discarded).isEqualTo(1);
        assertThat(find(appointment, NotificationType.CANCELLATION).orElseThrow().status())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(find(appointment, NotificationType.DENTIST_CANCELLATION).orElseThrow().status())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(find(appointment, NotificationType.RESCHEDULE).orElseThrow().status())
                .isEqualTo(NotificationStatus.DISCARDED);
    }

    @Test
    void discardPendingOfTypeDescartaSoloEseTipoYSoloSiEstaPendiente() {
        enqueue(appointment, NotificationType.REMINDER, NOW);
        enqueue(appointment, NotificationType.RESCHEDULE, NOW);

        int discarded = transactions.execute(status -> repository.discardPendingOfType(appointment,
                NotificationType.REMINDER, NotificationErrorCode.REMINDER_WINDOW_PASSED, NOW));
        int again = transactions.execute(status -> repository.discardPendingOfType(appointment,
                NotificationType.REMINDER, NotificationErrorCode.REMINDER_WINDOW_PASSED, NOW));

        assertThat(discarded).isEqualTo(1);
        assertThat(again).isZero();
        assertThat(find(appointment, NotificationType.REMINDER).orElseThrow().lastError())
                .isEqualTo("REMINDER_WINDOW_PASSED");
        assertThat(find(appointment, NotificationType.RESCHEDULE).orElseThrow().status())
                .isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void claimNextDueRespetaScheduledForOrdenYEstado() {
        UUID second = newAppointment("2026-12-11T13:00:00Z", "2026-12-11T13:30:00Z");
        UUID third = newAppointment("2026-12-12T13:00:00Z", "2026-12-12T13:30:00Z");
        enqueue(second, NotificationType.CONFIRMATION, NOW.minusSeconds(60));
        enqueue(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(120));
        enqueue(third, NotificationType.CONFIRMATION, NOW.plusSeconds(60));

        Optional<UUID> first = transactions.execute(status -> repository.claimNextDue(NOW).map(Notification::appointmentId));

        assertThat(first).contains(appointment);

        transactions.executeWithoutResult(status -> repository.discardPendingForCancellation(appointment,
                NotificationErrorCode.EXPIRED, NOW));
        Optional<UUID> next = transactions.execute(status -> repository.claimNextDue(NOW).map(Notification::appointmentId));
        assertThat(next).contains(second);

        transactions.executeWithoutResult(status -> repository.discardPendingForCancellation(second,
                NotificationErrorCode.EXPIRED, NOW));
        Optional<UUID> none = transactions.execute(status -> repository.claimNextDue(NOW).map(Notification::appointmentId));
        assertThat(none).isEmpty();
    }

    @Test
    void dosHilosNuncaTomanLaMismaFilaConSkipLocked() throws Exception {
        UUID second = newAppointment("2026-12-11T13:00:00Z", "2026-12-11T13:30:00Z");
        enqueue(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(120));
        enqueue(second, NotificationType.CONFIRMATION, NOW.minusSeconds(60));
        CountDownLatch firstHolds = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> holder = pool.submit(() -> transactions.execute(status -> {
                UUID claimed = repository.claimNextDue(NOW).orElseThrow().appointmentId();
                firstHolds.countDown();
                awaitQuietly(release);
                return claimed;
            }));
            assertThat(firstHolds.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Optional<UUID>> other = pool.submit(() -> transactions.execute(
                    status -> repository.claimNextDue(NOW).map(Notification::appointmentId)));
            Optional<UUID> secondClaim = other.get(10, TimeUnit.SECONDS);
            release.countDown();
            UUID firstClaim = holder.get(10, TimeUnit.SECONDS);

            assertThat(firstClaim).isEqualTo(appointment);
            assertThat(secondClaim).contains(second);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void conUnaSolaFilaRetenidaElSegundoHiloObtieneVacioSinEsperar() throws Exception {
        enqueue(appointment, NotificationType.CONFIRMATION, NOW.minusSeconds(120));
        CountDownLatch firstHolds = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> holder = pool.submit(() -> transactions.execute(status -> {
                UUID claimed = repository.claimNextDue(NOW).orElseThrow().appointmentId();
                firstHolds.countDown();
                awaitQuietly(release);
                return claimed;
            }));
            assertThat(firstHolds.await(10, TimeUnit.SECONDS)).isTrue();

            Optional<UUID> secondClaim = pool.submit(() -> transactions.execute(
                    status -> repository.claimNextDue(NOW).map(Notification::appointmentId))).get(10, TimeUnit.SECONDS);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);

            assertThat(secondClaim).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void dosEncoladosConcurrentesDelMismoTurnoYTipoDejanUnaFilaSinExcepcion() throws Exception {
        int threads = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    awaitQuietly(start);
                    enqueue(appointment, NotificationType.CONFIRMATION, NOW);
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(count(appointment)).isEqualTo(1);
    }

    private int count(UUID appointmentId) {
        return AppointmentFixtures.count("SELECT count(*) FROM notification WHERE appointment_id = '" + appointmentId + "'");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
