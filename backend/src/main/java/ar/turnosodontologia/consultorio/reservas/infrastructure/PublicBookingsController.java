package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.api.CreateBookingRequest;
import ar.turnosodontologia.consultorio.reservas.api.CreateBookingResponse;
import ar.turnosodontologia.consultorio.reservas.application.PublicBookingCommand;
import ar.turnosodontologia.consultorio.reservas.application.PublicBookingResult;
import ar.turnosodontologia.consultorio.reservas.application.PublicBookingUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público de reserva (US-030). El honeypot se responde de forma genérica, sin efectos. */
@RestController
@RequestMapping("/api/v1/public/bookings")
public class PublicBookingsController {

    private final PublicBookingUseCase useCase;

    PublicBookingsController(PublicBookingUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping
    public ResponseEntity<CreateBookingResponse> book(
            @Valid @RequestBody CreateBookingRequest request, HttpServletRequest httpRequest) {
        if (request.website() != null && !request.website().isBlank()) {
            return ResponseEntity.status(HttpStatus.OK).body(CreateBookingResponse.generic());
        }
        PublicBookingResult result = useCase.book(new PublicBookingCommand(
                request.serviceId(), request.startsAt(), request.firstName(), request.lastName(),
                request.dni(), request.phone(), request.email()), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateBookingResponse(
                result.bookingId(), result.serviceName(), result.startsAt(), result.endsAt(),
                result.status(), result.practiceName()));
    }
}
