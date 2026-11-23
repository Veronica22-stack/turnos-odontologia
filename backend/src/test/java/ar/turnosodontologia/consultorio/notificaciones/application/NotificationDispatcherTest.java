package ar.turnosodontologia.consultorio.notificaciones.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import ar.turnosodontologia.consultorio.notificaciones.domain.ManagementTokenSource;
import ar.turnosodontologia.consultorio.notificaciones.domain.Notification;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRetryPolicy;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationStatus;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.pacientes.api.PatientContactLookup;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Ramas del despacho que no se pueden armar con datos reales (FK) o que protegen a la cola de filas venenosas. */
class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");

    private NotificationRepository repository;
    private AppointmentNotificationLookup appointments;
    private EmailSender sender;
    private NotificationDispatcher dispatcher;
    private Notification pending;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        appointments = mock(AppointmentNotificationLookup.class);
        sender = mock(EmailSender.class);
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        when(transactions.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        dispatcher = new NotificationDispatcher(repository, appointments, mock(PatientContactLookup.class),
                mock(PracticeRepository.class), sender, ManagementTokenSource.none(), NotificationRetryPolicy.standard(5),
                Clock.fixed(NOW, ZoneOffset.UTC), transactions, "");
        pending = Notification.pending(UUID.randomUUID(), UUID.randomUUID(), NotificationType.REMINDER,
                NOW.minusSeconds(60), NOW.minusSeconds(3600));
        when(repository.claimNextDue(NOW)).thenReturn(Optional.of(pending)).thenReturn(Optional.empty());
    }

    @Test
    void unTurnoInexistenteDescartaLaNotificacionSinEnviar() {
        when(appointments.find(pending.appointmentId())).thenReturn(Optional.empty());

        dispatcher.dispatchDue();

        assertThat(pending.status()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(pending.lastError()).isEqualTo("APPOINTMENT_NOT_FOUND");
        verify(repository).save(pending);
        verify(sender, never()).send(any());
    }

    @Test
    void unFalloInesperadoAlConsultarElTurnoReprogramaEnVezDeBloquearLaCola() {
        when(appointments.find(pending.appointmentId())).thenThrow(new IllegalStateException("boom"));

        dispatcher.dispatchDue();

        assertThat(pending.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(pending.attempts()).isEqualTo(1);
        assertThat(pending.lastError()).isEqualTo("UNEXPECTED");
        assertThat(pending.scheduledFor()).isEqualTo(NOW.plusSeconds(300));
        verify(repository).save(pending);
    }

    // ---- APP_BASE_URL solo se valida si hay emisor de links de gestion (design D11) ----

    private static ManagementTokenSource tokenSourceEnabled() {
        return new ManagementTokenSource() {
            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public Optional<String> tokenFor(UUID appointmentId, NotificationType type) {
                return Optional.of("abc123");
            }
        };
    }

    private NotificationDispatcher withLinks(String appBaseUrl) {
        return new NotificationDispatcher(repository, appointments, mock(PatientContactLookup.class),
                mock(PracticeRepository.class), sender, tokenSourceEnabled(), NotificationRetryPolicy.standard(5),
                Clock.fixed(NOW, ZoneOffset.UTC), mock(TransactionTemplate.class), appBaseUrl);
    }

    @Test
    void conEmisorDeLinksUnaBaseUrlInvalidaHaceFallarElArranqueNombrandoLaVariable() {
        for (String invalid : new String[] {"", "turnos.ejemplo.com", "https://turnos.ejemplo.com/", "ftp://x.com"}) {
            assertThatThrownBy(() -> withLinks(invalid)).as(invalid).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_BASE_URL");
        }
    }

    @Test
    void conEmisorDeLinksUnaBaseUrlHttpOHttpsValidaSeAcepta() {
        assertThatCode(() -> withLinks("https://turnos.ejemplo.com")).doesNotThrowAnyException();
        assertThatCode(() -> withLinks("http://localhost:5173")).doesNotThrowAnyException();
    }
}
