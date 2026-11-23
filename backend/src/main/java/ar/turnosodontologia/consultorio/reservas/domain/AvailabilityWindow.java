package ar.turnosodontologia.consultorio.reservas.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Interpreta fechas locales, DST y la ventana de reserva según la audiencia. */
public final class AvailabilityWindow {

    private final Clock clock;
    private final BookingSettings settings;
    private final Audience audience;

    public AvailabilityWindow(Clock clock, BookingSettings settings, Audience audience) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.audience = Objects.requireNonNull(audience, "audience");
    }

    public Interval localDayInterval(LocalDate date) {
        ZoneId zone = settings.timezone();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
        return new Interval(start, end);
    }

    public Optional<Instant> instantOf(LocalDate date, LocalTime time) {
        LocalDateTime local = date.atTime(time);
        ZoneRules rules = settings.timezone().getRules();
        List<ZoneOffset> validOffsets = rules.getValidOffsets(local);
        if (validOffsets.isEmpty()) {
            return Optional.empty();
        }
        Instant instant = local.toInstant(validOffsets.getFirst());
        if (!LocalDateTime.ofInstant(instant, settings.timezone()).equals(local)) {
            return Optional.empty();
        }
        return Optional.of(instant);
    }

    public boolean isWithinHorizon(LocalDate date) {
        if (audience == Audience.INTERNAL) {
            return true;
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), settings.timezone());
        return !date.isAfter(today.plusDays(settings.maxAdvanceDays()));
    }

    public boolean isBookable(Instant startsAt) {
        if (audience == Audience.INTERNAL) {
            return true;
        }
        return !startsAt.isBefore(clock.instant().plus(settings.minNotice()));
    }

    public List<Interval> filter(List<Interval> slots) {
        if (audience == Audience.INTERNAL) {
            return slots;
        }
        return slots.stream().filter(slot -> isBookable(slot.start())).toList();
    }
}
