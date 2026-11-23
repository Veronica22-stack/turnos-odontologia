package ar.turnosodontologia.consultorio.reservas.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Solicitud de reserva pública. El campo {@code website} es honeypot: debe viajar vacío. */
public record CreateBookingRequest(
        @NotNull UUID serviceId,
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 20) String dni,
        @NotBlank @Size(max = 50) String phone,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 100) String website,
        @NotNull @AssertTrue Boolean privacyAccepted) {
}
