package ar.turnosodontologia.consultorio.notificaciones.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Reglas de encolado por evento (design D4): idempotente por (turno, tipo) y recordatorio solo si es futuro. */
class NotificationSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final UUID PRACTICE = UUID.randomUUID();
    private static final UUID APPOINTMENT = UUID.randomUUID();

    private NotificationRepository repository;
    private PracticeRepository practices;
    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        practices = mock(PracticeRepository.class);
        scheduler = new NotificationScheduler(repository, practices, Clock.fixed(NOW, ZoneOffset.UTC));
        givenReminderHoursBefore(24);
    }

    private void givenReminderHoursBefore(int hours) {
        Practice practice = Practice.existing(PRACTICE, "Consultorio", "America/Argentina/Buenos_Aires", null, null,
                null, 15, 120, 30, hours);
        when(practices.findById(PRACTICE)).thenReturn(Optional.of(practice));
    }

    @Test
    void confirmadoEncolaConfirmacionAhoraYRecordatorioSegunLaAnticipacionDelConsultorio() {
        scheduler.onConfirmed(PRACTICE, APPOINTMENT, Instant.parse("2026-12-10T13:00:00Z"));

        verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.CONFIRMATION, NOW, NOW);
        verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.REMINDER,
                Instant.parse("2026-12-09T13:00:00Z"), NOW);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void laAnticipacionSalePorLoVigenteEnElConsultorioNoDeUnValorFijo() {
        givenReminderHoursBefore(48);

        scheduler.onConfirmed(PRACTICE, APPOINTMENT, Instant.parse("2026-12-10T13:00:00Z"));

        verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.REMINDER,
                Instant.parse("2026-12-08T13:00:00Z"), NOW);
    }

    @Test
    void confirmadoDentroDeLaVentanaNoEncolaRecordatorio() {
        scheduler.onConfirmed(PRACTICE, APPOINTMENT, NOW.plusSeconds(3 * 3600));

        verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.CONFIRMATION, NOW, NOW);
        verify(repository, never()).enqueueIfAbsent(any(), any(), eq(NotificationType.REMINDER), any(), any());
    }

    @Test
    void reprogramadoReseteaLaReprogramacionYElRecordatorioConElNuevoInstante() {
        scheduler.onRescheduled(PRACTICE, APPOINTMENT, Instant.parse("2026-12-15T13:00:00Z"));

        verify(repository).upsertPending(PRACTICE, APPOINTMENT, NotificationType.RESCHEDULE, NOW, NOW);
        verify(repository).upsertPending(PRACTICE, APPOINTMENT, NotificationType.REMINDER,
                Instant.parse("2026-12-14T13:00:00Z"), NOW);
        verify(repository, never()).discardPendingOfType(any(), any(), any(), any());
    }

    @Test
    void reprogramadoDentroDeLaVentanaDescartaElRecordatorioPendiente() {
        scheduler.onRescheduled(PRACTICE, APPOINTMENT, NOW.plusSeconds(3 * 3600));

        verify(repository).upsertPending(PRACTICE, APPOINTMENT, NotificationType.RESCHEDULE, NOW, NOW);
        verify(repository).discardPendingOfType(APPOINTMENT, NotificationType.REMINDER,
                NotificationErrorCode.REMINDER_WINDOW_PASSED, NOW);
        verify(repository, never()).upsertPending(any(), any(), eq(NotificationType.REMINDER), any(), any());
    }

    @Test
    void canceladoPorElOdontologoDescartaPendientesYEncolaSoloLaCancelacion() {
        scheduler.onCancelled(PRACTICE, APPOINTMENT, false);

        InOrder order = inOrder(repository);
        order.verify(repository).discardPendingForCancellation(APPOINTMENT, NotificationErrorCode.APPOINTMENT_CANCELLED,
                NOW);
        order.verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.CANCELLATION, NOW, NOW);
        verify(repository, never()).enqueueIfAbsent(any(), any(), eq(NotificationType.DENTIST_CANCELLATION), any(),
                any());
    }

    @Test
    void canceladoPorElPacienteEncolaAdemasElAvisoAlOdontologo() {
        scheduler.onCancelled(PRACTICE, APPOINTMENT, true);

        InOrder order = inOrder(repository);
        order.verify(repository).discardPendingForCancellation(APPOINTMENT, NotificationErrorCode.APPOINTMENT_CANCELLED,
                NOW);
        order.verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.CANCELLATION, NOW, NOW);
        order.verify(repository).enqueueIfAbsent(PRACTICE, APPOINTMENT, NotificationType.DENTIST_CANCELLATION, NOW,
                NOW);
    }
}
