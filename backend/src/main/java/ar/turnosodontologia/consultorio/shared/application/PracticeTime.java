package ar.turnosodontologia.consultorio.shared.application;

import ar.turnosodontologia.consultorio.shared.domain.InstantRange;
import ar.turnosodontologia.consultorio.shared.domain.ZoneConversions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/** Reloj unico combinado con la zona del consultorio de la instalacion (design D9). */
@Component
public class PracticeTime {

    private final Clock clock;
    private final InstallationPractice practice;

    public PracticeTime(Clock clock, InstallationPractice practice) {
        this.clock = clock;
        this.practice = practice;
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return toLocal(now()).toLocalDate();
    }

    public LocalDateTime toLocal(Instant instant) {
        return ZoneConversions.toLocal(instant, practice.zone());
    }

    public Instant toInstant(LocalDateTime local) {
        return ZoneConversions.toInstant(local, practice.zone());
    }

    public InstantRange dayBounds(LocalDate day) {
        return ZoneConversions.dayBounds(day, practice.zone());
    }
}
