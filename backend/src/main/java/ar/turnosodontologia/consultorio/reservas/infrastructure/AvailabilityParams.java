package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.UUID;

/** Parseo compartido de los parámetros HTTP de disponibilidad, sin exponer valores rechazados. */
final class AvailabilityParams {

    private AvailabilityParams() {
    }

    static Parsed parse(String serviceId, String date) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new ValidationException("serviceId", "REQUIRED");
        }
        if (date == null || date.isBlank()) {
            throw new ValidationException("date", "REQUIRED");
        }
        UUID parsedId;
        try {
            parsedId = UUID.fromString(serviceId);
        } catch (IllegalArgumentException error) {
            throw new ValidationException("serviceId", "UUID");
        }
        LocalDate parsedDate;
        try {
            parsedDate = LocalDate.parse(date);
        } catch (DateTimeException error) {
            throw new ValidationException("date", "ISO_LOCAL_DATE");
        }
        return new Parsed(parsedId, parsedDate);
    }

    record Parsed(UUID serviceId, LocalDate date) {
    }
}
