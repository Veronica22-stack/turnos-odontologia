package ar.turnosodontologia.consultorio.reservas.application;

import ar.turnosodontologia.consultorio.agenda.api.ActiveBookingsLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.api.PublicServiceNameLookup;
import ar.turnosodontologia.consultorio.pacientes.api.PatientBookingOperations;
import ar.turnosodontologia.consultorio.reservas.domain.BookingToken;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenGenerator;
import ar.turnosodontologia.consultorio.reservas.domain.BookingTokenRepository;
import ar.turnosodontologia.consultorio.reservas.domain.TokenHasher;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettings;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettingsLookup;
import ar.turnosodontologia.consultorio.shared.api.RateLimiter;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reserva pública (C-14): valida, resuelve paciente, crea turno y emite el token hasheado en una transacción. */
@Service
public class PublicBookingUseCase {

    /** Límite provisional de turnos futuros activos por contacto mientras PA-05 siga abierta. */
    public static final int MAX_ACTIVE_FUTURE_BOOKINGS_PER_CONTACT = 3;
    public static final String PRIVACY_NOTICE_VERSION = "PRIVACY_NOTICE_V1";

    private static final Logger LOG = LoggerFactory.getLogger(PublicBookingUseCase.class);

    private final PublicSlotLookup slots;
    private final PracticeBookingSettingsLookup bookingSettings;
    private final PatientBookingOperations patients;
    private final ActiveBookingsLookup activeBookings;
    private final AppointmentOperations appointments;
    private final PublicServiceNameLookup serviceNames;
    private final BookingTokenRepository tokens;
    private final BookingTokenGenerator generator;
    private final BookingRateLimiter rateLimiter;
    private final InstallationPractice practice;
    private final PracticeRepository practices;
    private final Clock clock;
    private final Duration tokenTtl;

    public PublicBookingUseCase(
            PublicSlotLookup slots,
            PracticeBookingSettingsLookup bookingSettings,
            PatientBookingOperations patients,
            ActiveBookingsLookup activeBookings,
            AppointmentOperations appointments,
            PublicServiceNameLookup serviceNames,
            BookingTokenRepository tokens,
            BookingTokenGenerator generator,
            BookingRateLimiter rateLimiter,
            InstallationPractice practice,
            PracticeRepository practices,
            Clock clock,
            @Value("${BOOKING_TOKEN_TTL_DAYS:1}") long tokenTtlDays) {
        if (tokenTtlDays <= 0) {
            throw new IllegalArgumentException("BOOKING_TOKEN_TTL_DAYS debe ser mayor que 0");
        }
        this.slots = slots;
        this.bookingSettings = bookingSettings;
        this.patients = patients;
        this.activeBookings = activeBookings;
        this.appointments = appointments;
        this.serviceNames = serviceNames;
        this.tokens = tokens;
        this.generator = generator;
        this.rateLimiter = rateLimiter;
        this.practice = practice;
        this.practices = practices;
        this.clock = clock;
        this.tokenTtl = Duration.ofDays(tokenTtlDays);
    }

    @Transactional
    public PublicBookingResult book(PublicBookingCommand request, String clientIp) {
        Instant now = clock.instant();
        requireRateLimit(clientIp, now);
        UUID practiceId = practice.id();
        requireOfferedSlot(request.serviceId(), request.startsAt(), practiceId, now);

        List<UUID> contactIds = patients.findIdsByContact(
                practiceId, request.dni(), request.email(), request.phone());
        long active = activeBookings.countActiveFuture(practiceId, contactIds, now);
        if (active >= MAX_ACTIVE_FUTURE_BOOKINGS_PER_CONTACT) {
            throw new BusinessRuleException("BOOKING_CONTACT_LIMIT");
        }

        UUID patientId = patients.findOrCreate(practiceId, request.dni(), request.firstName(), request.lastName(),
                request.phone(), request.email()).patientId();
        AppointmentView appointment = bookAppointment(patientId, request.serviceId(), request.startsAt());
        issueToken(appointment, now);

        LOG.info("Reserva publica confirmada appointmentId={} patientId={} serviceId={} practiceId={}",
                appointment.id(), patientId, request.serviceId(), practiceId);
        return new PublicBookingResult(appointment.id(), serviceNames.findName(practiceId, request.serviceId()).orElse(""),
                appointment.startsAt(), appointment.endsAt(), appointment.status().name(), practiceName(practiceId));
    }

    private void requireRateLimit(String clientIp, Instant now) {
        rateLimiter.tryAcquire("booking|" + (clientIp == null ? "" : clientIp), clock)
                .ifPresent(retryAt -> {
                    throw new RateLimitedException(RateLimiter.retryAfterSeconds(now, retryAt));
                });
    }

    private void requireOfferedSlot(UUID serviceId, Instant startsAt, UUID practiceId, Instant now) {
        if (startsAt == null || !startsAt.isAfter(now)) {
            throw new BusinessRuleException("APPOINTMENT_IN_PAST");
        }
        PracticeBookingSettings settings = bookingSettings.findBookingSettings(practiceId);
        if (startsAt.isBefore(now.plus(Duration.ofMinutes(settings.minNoticeMin())))) {
            throw new BusinessRuleException("BOOKING_TOO_SOON");
        }
        if (startsAt.isAfter(now.plus(Duration.ofDays(settings.maxAdvanceDays())))) {
            throw new BusinessRuleException("BOOKING_BEYOND_HORIZON");
        }
        if (!slots.isOffered(practiceId, serviceId, startsAt.atZone(practice.zone()).toLocalDate(), startsAt)) {
            throw new ConflictException("SLOT_TAKEN");
        }
    }

    private AppointmentView bookAppointment(UUID patientId, UUID serviceId, Instant startsAt) {
        try {
            return appointments.book(new ar.turnosodontologia.consultorio.agenda.api.PublicBookingCommand(
                    patientId, serviceId, startsAt));
        } catch (DomainException error) {
            String code = error.code();
            if ("APPOINTMENT_SLOT_TAKEN".equals(code) || "APPOINTMENT_SLOT_BLOCKED".equals(code)
                    || "OUTSIDE_WORKING_HOURS".equals(code)) {
                throw new ConflictException("SLOT_TAKEN");
            }
            throw error;
        }
    }

    private void issueToken(AppointmentView appointment, Instant now) {
        String raw = generator.generate();
        BookingToken token = BookingToken.create(
                appointment.id(),
                TokenHasher.hash(raw),
                appointment.endsAt().plus(tokenTtl),
                now,
                PRIVACY_NOTICE_VERSION,
                now);
        tokens.save(token);
        // El token en claro se usa solo en el armado del link de C-13; nunca se loguea ni se guarda.
    }

    private String practiceName(UUID practiceId) {
        return practices.findById(practiceId).map(Practice::name).orElse("");
    }
}
