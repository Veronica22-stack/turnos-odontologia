package ar.turnosodontologia.consultorio.reservas.application;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.api.BlockedRanges;
import ar.turnosodontologia.consultorio.agenda.api.PublicService;
import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.agenda.api.WeeklyInterval;
import ar.turnosodontologia.consultorio.agenda.api.WorkingHoursLookup;
import ar.turnosodontologia.consultorio.reservas.domain.Audience;
import ar.turnosodontologia.consultorio.reservas.domain.AvailabilityCalculator;
import ar.turnosodontologia.consultorio.reservas.domain.AvailabilityWindow;
import ar.turnosodontologia.consultorio.reservas.domain.BookingSettings;
import ar.turnosodontologia.consultorio.reservas.domain.Interval;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettingsLookup;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Orquesta la disponibilidad a partir de puertos de solo lectura y dominio puro. */
@Service
public class AvailabilityUseCase {

    private final WorkingHoursLookup workingHours;
    private final ServiceAvailabilityLookup services;
    private final PracticeBookingSettingsLookup settings;
    private final AffectedAppointments appointments;
    private final BlockedRanges blockedRanges;
    private final Clock clock;

    public AvailabilityUseCase(
            WorkingHoursLookup workingHours,
            ServiceAvailabilityLookup services,
            PracticeBookingSettingsLookup settings,
            AffectedAppointments appointments,
            BlockedRanges blockedRanges,
            Clock clock) {
        this.workingHours = workingHours;
        this.services = services;
        this.settings = settings;
        this.appointments = appointments;
        this.blockedRanges = blockedRanges;
        this.clock = clock;
    }

    public AvailabilityOutcome findAvailability(AvailabilityQuery query) {
        UUID practiceId = query.practiceId();
        boolean isPublic = query.audience() == Audience.PUBLIC;
        if (isPublic && !services.hasPublicServices(practiceId)) {
            return new AvailabilityOutcome(true, List.of());
        }

        PublicService service = services.findPublicService(practiceId, query.serviceId())
                .orElseThrow(() -> new NotFoundException("SERVICE_NOT_FOUND"));
        requireUsable(service, isPublic);

        BookingSettings bookingSettings = currentSettings(practiceId);
        AvailabilityWindow window = new AvailabilityWindow(clock, bookingSettings, query.audience());
        if (isPublic && !window.isWithinHorizon(query.date())) {
            return new AvailabilityOutcome(false, List.of());
        }

        List<Interval> working = workingIntervals(window, query, bookingSettings);
        if (working.isEmpty()) {
            return new AvailabilityOutcome(false, List.of());
        }
        List<Interval> occupied = occupiedIntervals(window.localDayInterval(query.date()), practiceId);
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                working, occupied, service.durationMin(), bookingSettings.slotGranularity());
        return new AvailabilityOutcome(false, window.filter(slots));
    }

    private void requireUsable(PublicService service, boolean isPublic) {
        if (!service.active()) {
            throw new BusinessRuleException("SERVICE_INACTIVE");
        }
        if (isPublic && !service.publicVisible()) {
            throw new NotFoundException("SERVICE_NOT_FOUND");
        }
    }

    private BookingSettings currentSettings(UUID practiceId) {
        var data = settings.findBookingSettings(practiceId);
        return new BookingSettings(
                ZoneId.of(data.timezone()),
                Duration.ofMinutes(data.slotGranularityMin()),
                Duration.ofMinutes(data.minNoticeMin()),
                data.maxAdvanceDays());
    }

    private List<Interval> workingIntervals(
            AvailabilityWindow window, AvailabilityQuery query, BookingSettings bookingSettings) {
        int weekday = query.date().getDayOfWeek().getValue();
        List<Interval> result = new ArrayList<>();
        for (WeeklyInterval weekly : workingHours.findWeeklyIntervals(query.practiceId())) {
            if (weekly.weekday() != weekday) {
                continue;
            }
            window.instantOf(query.date(), weekly.startTime())
                    .flatMap(start -> window.instantOf(query.date(), weekly.endTime())
                            .map(end -> new Interval(start, end)))
                    .ifPresent(result::add);
        }
        return result;
    }

    private List<Interval> occupiedIntervals(Interval day, UUID practiceId) {
        List<Interval> result = new ArrayList<>();
        appointments.findWithin(practiceId, day.start(), day.end()).stream()
                .map(item -> new Interval(item.startsAt(), item.endsAt()))
                .forEach(result::add);
        blockedRanges.findOverlapping(practiceId, day.start(), day.end()).stream()
                .map(item -> new Interval(item.startsAt(), item.endsAt()))
                .forEach(result::add);
        return result;
    }
}
