package ar.turnosodontologia.consultorio.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Transiciones de la notificacion como fila de una cola (design D4, D6, D7). */
class NotificationTest {

    private static final Instant NOW = Instant.parse("2026-12-01T12:00:00Z");
    private static final Instant NEXT = Instant.parse("2026-12-01T12:05:00Z");

    private Notification pending() {
        return Notification.pending(UUID.randomUUID(), UUID.randomUUID(), NotificationType.CONFIRMATION, NOW, NOW);
    }

    @Test
    void unaNotificacionNuevaEstaPendienteSinIntentos() {
        Notification notification = pending();

        assertThat(notification.id()).isNotNull();
        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isZero();
        assertThat(notification.scheduledFor()).isEqualTo(NOW);
        assertThat(notification.sentAt()).isNull();
        assertThat(notification.lastError()).isNull();
    }

    @Test
    void marcarEnviadaGuardaSentAt() {
        Notification notification = pending();

        notification.markSent(NEXT);

        assertThat(notification.status()).isEqualTo(NotificationStatus.SENT);
        assertThat(notification.sentAt()).isEqualTo(NEXT);
    }

    @Test
    void reintentarIncrementaIntentosGuardaElCodigoYReprograma() {
        Notification notification = pending();

        notification.markRetry(NotificationErrorCode.SMTP_UNAVAILABLE, NEXT, NOW);
        notification.markRetry(NotificationErrorCode.UNEXPECTED, NEXT.plusSeconds(600), NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.attempts()).isEqualTo(2);
        assertThat(notification.scheduledFor()).isEqualTo(NEXT.plusSeconds(600));
        assertThat(notification.lastError()).isEqualTo("UNEXPECTED");
    }

    @Test
    void fallarIncrementaIntentosYQuedaFallida() {
        Notification notification = pending();

        notification.markFailed(NotificationErrorCode.SMTP_REJECTED, NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.attempts()).isEqualTo(1);
        assertThat(notification.lastError()).isEqualTo("SMTP_REJECTED");
    }

    @Test
    void descartarNoCuentaIntentoYGuardaElMotivo() {
        Notification notification = pending();

        notification.markDiscarded(NotificationErrorCode.NO_RECIPIENT, NOW);

        assertThat(notification.status()).isEqualTo(NotificationStatus.DISCARDED);
        assertThat(notification.attempts()).isZero();
        assertThat(notification.lastError()).isEqualTo("NO_RECIPIENT");
    }

    @Test
    void desdeUnEstadoTerminalLasTransicionesDeEnvioFallan() {
        Notification sent = pending();
        sent.markSent(NOW);
        Notification failed = pending();
        failed.markFailed(NotificationErrorCode.SMTP_REJECTED, NOW);
        Notification discarded = pending();
        discarded.markDiscarded(NotificationErrorCode.EXPIRED, NOW);

        for (Notification terminal : new Notification[] {sent, failed, discarded}) {
            assertThatThrownBy(() -> terminal.markSent(NEXT)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> terminal.markRetry(NotificationErrorCode.UNEXPECTED, NEXT, NEXT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> terminal.markFailed(NotificationErrorCode.UNEXPECTED, NEXT))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> terminal.markDiscarded(NotificationErrorCode.EXPIRED, NEXT))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void toStringSoloMuestraIdentificadoresTipoYEstado() {
        Notification notification = pending();

        assertThat(notification.toString())
                .contains(notification.id().toString())
                .contains(notification.appointmentId().toString())
                .contains("CONFIRMATION")
                .contains("PENDING");
    }

    @Test
    void todoCodigoDelCatalogoCabeEnLaColumnaLastError() {
        for (NotificationErrorCode code : NotificationErrorCode.values()) {
            assertThat(code.name().length()).isLessThanOrEqualTo(64);
        }
    }
}
