package ar.turnosodontologia.consultorio.agenda.api;

import java.time.Duration;
import java.util.UUID;

/**
 * Prestación del consultorio vista desde disponibilidad: solo identificador, duración y estado.
 * Los estados se exponen para que el caso de uso distinga `SERVICE_INACTIVE` de `SERVICE_NOT_FOUND`.
 */
public record PublicService(UUID id, Duration durationMin, boolean active, boolean publicVisible) {
}
