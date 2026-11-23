package ar.turnosodontologia.consultorio.reservas.domain;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

/** Valores vigentes de configuración de reserva del consultorio. */
public record BookingSettings(
        ZoneId timezone,
        Duration slotGranularity,
        Duration minNotice,
        int maxAdvanceDays) {

    public BookingSettings {
        Objects.requireNonNull(timezone, "timezone");
        Objects.requireNonNull(slotGranularity, "slotGranularity");
        Objects.requireNonNull(minNotice, "minNotice");
    }
}
