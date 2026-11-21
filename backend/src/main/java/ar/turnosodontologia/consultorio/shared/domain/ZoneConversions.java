package ar.turnosodontologia.consultorio.shared.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Conversiones puras entre instantes UTC y hora local de una zona (design D9). */
public final class ZoneConversions {

    private ZoneConversions() {
    }

    public static LocalDateTime toLocal(Instant instant, ZoneId zone) {
        return LocalDateTime.ofInstant(instant, zone);
    }

    /**
     * Hora local a instante. Un hueco de horario lanza {@link NonexistentLocalTimeException}; una hora ambigua
     * (se repite al retroceder el reloj) usa el desfase mas temprano.
     */
    public static Instant toInstant(LocalDateTime local, ZoneId zone) {
        if (zone.getRules().getValidOffsets(local).isEmpty()) {
            throw new NonexistentLocalTimeException("La hora local " + local + " no existe en la zona " + zone);
        }
        return ZonedDateTime.ofLocal(local, zone, null).toInstant();
    }

    /** Limites {@code [inicio, fin)} en UTC del dia local indicado (puede durar 23 o 25 horas). */
    public static InstantRange dayBounds(LocalDate day, ZoneId zone) {
        return new InstantRange(
                day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
