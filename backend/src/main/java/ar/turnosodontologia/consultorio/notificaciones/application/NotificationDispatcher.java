package ar.turnosodontologia.consultorio.notificaciones.application;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailMessage;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSendException;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import ar.turnosodontologia.consultorio.notificaciones.domain.EmailTemplates;
import ar.turnosodontologia.consultorio.notificaciones.domain.ManagementTokenSource;
import ar.turnosodontologia.consultorio.notificaciones.domain.Notification;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationErrorCode;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRepository;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRetryPolicy;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import ar.turnosodontologia.consultorio.notificaciones.domain.RetryDecision;
import ar.turnosodontologia.consultorio.pacientes.api.PatientContactLookup;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Despacho de la cola (design D5-D8, D11, D16). Procesa de a una notificacion por transaccion, tomada con
 * {@code FOR UPDATE SKIP LOCKED}: un fallo no arrastra a las demas y varios despachadores (hilos o procesos) nunca
 * toman la misma. Antes de enviar relee el turno y el destinatario; el contenido se arma al enviar y nunca se
 * persiste. Los logs y {@code last_error} llevan solo identificadores, tipos y codigos del catalogo cerrado.
 */
@Service
public class NotificationDispatcher {

    /** Maximo de notificaciones por pasada: el resto queda para la siguiente. */
    public static final int BATCH_SIZE = 20;

    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);

    /** Tipos para los que el email ofrece gestionar (cancelar o reprogramar) el turno. */
    private static final Set<NotificationType> MANAGEABLE =
            EnumSet.of(NotificationType.CONFIRMATION, NotificationType.REMINDER, NotificationType.RESCHEDULE);

    /** Tipos que se envian aunque el turno ya este cancelado o iniciado. */
    private static final Set<NotificationType> ALWAYS_SENT =
            EnumSet.of(NotificationType.CANCELLATION, NotificationType.DENTIST_CANCELLATION);

    private final NotificationRepository notifications;
    private final AppointmentNotificationLookup appointments;
    private final PatientContactLookup patients;
    private final PracticeRepository practices;
    private final EmailSender sender;
    private final ManagementTokenSource tokens;
    private final NotificationRetryPolicy retryPolicy;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final String appBaseUrl;

    public NotificationDispatcher(NotificationRepository notifications, AppointmentNotificationLookup appointments,
            PatientContactLookup patients, PracticeRepository practices, EmailSender sender,
            ManagementTokenSource tokens, NotificationRetryPolicy retryPolicy, Clock clock,
            TransactionTemplate transactions, @Value("${APP_BASE_URL:}") String appBaseUrl) {
        this.notifications = notifications;
        this.appointments = appointments;
        this.patients = patients;
        this.practices = practices;
        this.sender = sender;
        this.tokens = tokens;
        this.retryPolicy = retryPolicy;
        this.clock = clock;
        this.transactions = transactions;
        this.appBaseUrl = appBaseUrl;
        if (tokens.enabled() && !isValidBaseUrl(appBaseUrl)) {
            throw new IllegalStateException("Configuracion invalida: APP_BASE_URL debe ser una URL absoluta http(s) "
                    + "sin barra final cuando hay un emisor de links de gestion");
        }
    }

    /** URL absoluta http(s) con host y sin barra final (ni ruta): el link se arma como base + "/turno/" + token. */
    private static boolean isValidBaseUrl(String url) {
        if (url == null || url.isBlank() || url.endsWith("/")) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    /** Procesa las notificaciones vencidas, en orden de {@code scheduled_for}; devuelve cuantas proceso. */
    public int dispatchDue() {
        int processed = 0;
        while (processed < BATCH_SIZE) {
            Boolean worked = transactions.execute(status -> processNext());
            if (!Boolean.TRUE.equals(worked)) {
                break;
            }
            processed++;
        }
        return processed;
    }

    private boolean processNext() {
        Instant now = clock.instant();
        Optional<Notification> claimed = notifications.claimNextDue(now);
        if (claimed.isEmpty()) {
            return false;
        }
        Notification notification = claimed.get();
        try {
            process(notification, now);
        } catch (EmailSendException error) {
            recordFailure(notification, error.code(), error.retryable(), now);
        } catch (RuntimeException error) {
            LOG.warn("Fallo inesperado al despachar notificationId={} appointmentId={} type={} error={}",
                    notification.id(), notification.appointmentId(), notification.type(),
                    error.getClass().getSimpleName());
            recordFailure(notification, NotificationErrorCode.UNEXPECTED, true, now);
        }
        notifications.save(notification);
        return true;
    }

    private void process(Notification notification, Instant now) {
        Optional<AppointmentNotificationView> found = appointments.find(notification.appointmentId());
        if (found.isEmpty()) {
            notification.markDiscarded(NotificationErrorCode.APPOINTMENT_NOT_FOUND, now);
            return;
        }
        AppointmentNotificationView appointment = found.get();
        if (!ALWAYS_SENT.contains(notification.type())) {
            if (appointment.status() != AppointmentStatusView.CONFIRMED) {
                notification.markDiscarded(NotificationErrorCode.APPOINTMENT_NOT_CONFIRMED, now);
                return;
            }
            if (!now.isBefore(appointment.startsAt())) {
                notification.markDiscarded(NotificationErrorCode.EXPIRED, now);
                return;
            }
        }
        Practice practice = practices.findById(notification.practiceId())
                .orElseThrow(() -> new IllegalStateException("Consultorio inexistente para la notificacion"));
        Optional<String> recipient = recipientOf(notification, appointment, practice);
        if (recipient.isEmpty()) {
            notification.markDiscarded(NotificationErrorCode.NO_RECIPIENT, now);
            return;
        }
        EmailMessage message = EmailTemplates.render(notification.type(), new EmailTemplates.Data(
                appointment.serviceName(), appointment.startsAt(), ZoneId.of(practice.timezone()), practice.name(),
                practice.address(), practice.contactPhone(), managementLink(notification)));
        sender.send(new OutgoingEmail(notification.id(), notification.appointmentId(), notification.type(),
                EmailTemplates.templateName(notification.type()), recipient.get(), message.subject(),
                message.body()));
        notification.markSent(now);
    }

    private Optional<String> recipientOf(Notification notification, AppointmentNotificationView appointment,
            Practice practice) {
        if (notification.type() == NotificationType.DENTIST_CANCELLATION) {
            return Optional.ofNullable(practice.contactEmail()).filter(email -> !email.isBlank());
        }
        return patients.findEmail(appointment.patientId());
    }

    /** Arma el link en memoria al enviar; el token jamas se persiste ni se loguea. */
    private String managementLink(Notification notification) {
        if (!tokens.enabled() || !MANAGEABLE.contains(notification.type())) {
            return null;
        }
        return tokens.tokenFor(notification.appointmentId(), notification.type())
                .map(token -> appBaseUrl + "/turno/" + token)
                .orElse(null);
    }

    private void recordFailure(Notification notification, NotificationErrorCode code, boolean retryable,
            Instant now) {
        RetryDecision decision = retryPolicy.decide(notification.attempts() + 1, retryable, now);
        if (decision.retry()) {
            notification.markRetry(code, decision.nextScheduledFor(), now);
            LOG.info("Notificacion reprogramada notificationId={} appointmentId={} type={} attempts={} error={}",
                    notification.id(), notification.appointmentId(), notification.type(), notification.attempts(),
                    code);
        } else {
            notification.markFailed(code, now);
            LOG.warn("ALERTA notificacion fallida notificationId={} appointmentId={} type={} attempts={} error={}",
                    notification.id(), notification.appointmentId(), notification.type(), notification.attempts(),
                    code);
        }
    }
}
