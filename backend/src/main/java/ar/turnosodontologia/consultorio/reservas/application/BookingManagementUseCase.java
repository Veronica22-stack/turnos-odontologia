package ar.turnosodontologia.consultorio.reservas.application;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentServiceLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestión del turno por token: ver, cancelar y reprogramar (US-031/US-032, Flujo 4). */
@Service
public class BookingManagementUseCase {

    private static final String INVALID_TOKEN = "INVALID_OR_EXPIRED_TOKEN";

    private final BookingTokenRepository tokens;
    private final AppointmentNotificationLookup appointments;
    private final AppointmentOperations operations;
    private final AppointmentServiceLookup appointmentServices;
    private final PublicSlotLookup slots;
    private final BookingRateLimiter rateLimiter;
    private final AuditLogger audit;
    private final PracticeRepository practices;
    private final InstallationPractice practice;
    private final Clock clock;
    private final Duration tokenTtl;

    public BookingManagementUseCase(
            BookingTokenRepository tokens,
            AppointmentNotificationLookup appointments,
            AppointmentOperations operations,
            AppointmentServiceLookup appointmentServices,
            PublicSlotLookup slots,
            BookingRateLimiter rateLimiter,
            AuditLogger audit,
            PracticeRepository practices,
            InstallationPractice practice,
            Clock clock,
            @Value("${BOOKING_TOKEN_TTL_DAYS:1}") long tokenTtlDays) {
        if (tokenTtlDays <= 0) {
            throw new IllegalArgumentException("BOOKING_TOKEN_TTL_DAYS debe ser mayor que 0");
        }
        this.tokens = tokens;
        this.appointments = appointments;
        this.operations = operations;
        this.appointmentServices = appointmentServices;
        this.slots = slots;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.practices = practices;
        this.practice = practice;
        this.clock = clock;
        this.tokenTtl = Duration.ofDays(tokenTtlDays);
    }

    // No es readOnly: toda consulta con token registra BOOKING_TOKEN_USE en audit_log (Flujo 4).
    @Transactional
    public BookingDetails view(String rawToken, String clientIp) {
        Resolved resolved = resolve(rawToken, clientIp);
        auditUse(resolved, "VIEW");
        return toDetails(resolved.appointment());
    }

    @Transactional
    public BookingDetails cancel(String rawToken, String clientIp) {
        Resolved resolved = resolve(rawToken, clientIp);
        requireModifiable(resolved.appointment());
        operations.cancel(resolved.appointment().appointmentId(), CancelledByView.PATIENT);
        tokens.revokeAllForAppointment(resolved.appointment().appointmentId(), clock.instant());
        auditUse(resolved, "CANCEL");
        return toDetails(appointments.find(resolved.appointment().appointmentId()).orElse(resolved.appointment()));
    }

    @Transactional
    public BookingDetails reschedule(String rawToken, Instant newStartsAt, String clientIp) {
        Resolved resolved = resolve(rawToken, clientIp);
        requireModifiable(resolved.appointment());
        if (newStartsAt == null || !newStartsAt.isAfter(clock.instant())) {
            throw new BusinessRuleException("APPOINTMENT_IN_PAST");
        }
        requireOfferedSlot(resolved.appointment(), newStartsAt);
        try {
            operations.reschedule(resolved.appointment().appointmentId(), newStartsAt, false);
        } catch (DomainException error) {
            if ("APPOINTMENT_SLOT_TAKEN".equals(error.code()) || "APPOINTMENT_SLOT_BLOCKED".equals(error.code())
                    || "OUTSIDE_WORKING_HOURS".equals(error.code())) {
                throw new ConflictException("SLOT_TAKEN");
            }
            throw error;
        }
        int durationMinutes = (int) Duration.between(
                resolved.appointment().startsAt(), resolved.appointment().endsAt()).toMinutes();
        Instant newEndsAt = newStartsAt.plus(Duration.ofMinutes(Math.max(durationMinutes, 0)));
        for (BookingToken token : tokens.findByAppointmentId(resolved.appointment().appointmentId())) {
            token.extendExpiration(newEndsAt.plus(tokenTtl));
        }
        auditUse(resolved, "RESCHEDULE");
        return toDetails(appointments.find(resolved.appointment().appointmentId()).orElse(resolved.appointment()));
    }

    private Resolved resolve(String rawToken, String clientIp) {
        requireRateLimit(clientIp);
        String hash;
        try {
            hash = TokenHasher.hash(rawToken);
        } catch (IllegalArgumentException error) {
            throw new NotFoundException(INVALID_TOKEN);
        }
        BookingToken token = tokens.findByTokenHash(hash)
                .filter(candidate -> TokenHasher.matches(rawToken, candidate.tokenHash()))
                .filter(candidate -> candidate.isUsable(clock.instant()))
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN));
        AppointmentNotificationView appointment = appointments.find(token.appointmentId())
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN));
        return new Resolved(token, appointment);
    }

    private void requireRateLimit(String clientIp) {
        rateLimiter.tryAcquire("token|" + (clientIp == null ? "" : clientIp), clock)
                .ifPresent(retryAt -> {
                    throw new RateLimitedException(RateLimiter.retryAfterSeconds(clock.instant(), retryAt));
                });
    }

    private void requireModifiable(AppointmentNotificationView appointment) {
        if (appointment.status() != AppointmentStatusView.CONFIRMED
                || !appointment.startsAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("BOOKING_MODIFICATION_NOT_ALLOWED");
        }
    }

    private void requireOfferedSlot(AppointmentNotificationView appointment, Instant newStartsAt) {
        UUID serviceId = appointmentServices.findServiceId(appointment.appointmentId())
                .orElseThrow(() -> new NotFoundException(INVALID_TOKEN));
        if (!slots.isOffered(practice.id(), serviceId,
                newStartsAt.atZone(practice.zone()).toLocalDate(), newStartsAt)) {
            throw new ConflictException("SLOT_TAKEN");
        }
    }

    private BookingDetails toDetails(AppointmentNotificationView appointment) {
        boolean modifiable = appointment.status() == AppointmentStatusView.CONFIRMED
                && appointment.startsAt().isAfter(clock.instant());
        return new BookingDetails(
                appointment.appointmentId(),
                appointment.serviceName(),
                practices.findById(practice.id()).map(Practice::name).orElse(""),
                appointment.startsAt(),
                appointment.endsAt(),
                appointment.status().name(),
                modifiable,
                modifiable);
    }

    private void auditUse(Resolved resolved, String outcome) {
        String reasonCode = switch (outcome) {
            case "VIEW" -> "TOKEN_VIEW";
            case "CANCEL" -> "TOKEN_CANCEL";
            case "RESCHEDULE" -> "TOKEN_RESCHEDULE";
            default -> "TOKEN_USE";
        };
        audit.log(AuditEvent.of(
                AuditAction.BOOKING_TOKEN_USE,
                null,
                resolved.appointment().patientId(),
                "booking_token",
                resolved.appointment().appointmentId().toString(),
                null,
                null,
                Map.of("outcome", outcome, "reason_code", reasonCode)));
    }

    private record Resolved(BookingToken token, AppointmentNotificationView appointment) {
    }
}
