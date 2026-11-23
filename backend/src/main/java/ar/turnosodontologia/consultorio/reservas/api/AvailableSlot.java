package ar.turnosodontologia.consultorio.reservas.api;

import java.time.Instant;

/** Slot reservable: solo instantes UTC, sin identificadores de turno ni datos personales. */
public record AvailableSlot(Instant startsAt, Instant endsAt) {
}
