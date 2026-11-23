package ar.turnosodontologia.consultorio.reservas.infrastructure;

import ar.turnosodontologia.consultorio.reservas.api.BookingView;
import ar.turnosodontologia.consultorio.reservas.api.RescheduleBookingRequest;
import ar.turnosodontologia.consultorio.reservas.application.BookingDetails;
import ar.turnosodontologia.consultorio.reservas.application.BookingManagementUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gestión autogestionada del turno desde el link del email (US-031/US-032). */
@RestController
@RequestMapping("/api/v1/public/bookings")
public class BookingManagementController {

    private final BookingManagementUseCase useCase;

    BookingManagementController(BookingManagementUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/{token}")
    public BookingView view(@PathVariable String token, HttpServletRequest request) {
        return toView(useCase.view(token, request.getRemoteAddr()));
    }

    @DeleteMapping("/{token}")
    public BookingView cancel(@PathVariable String token, HttpServletRequest request) {
        return toView(useCase.cancel(token, request.getRemoteAddr()));
    }

    @PostMapping("/{token}/reschedule")
    public BookingView reschedule(@PathVariable String token,
            @Valid @RequestBody RescheduleBookingRequest body, HttpServletRequest request) {
        return toView(useCase.reschedule(token, body.startsAt(), request.getRemoteAddr()));
    }

    private static BookingView toView(BookingDetails details) {
        return new BookingView(details.bookingId(), details.serviceName(), details.practiceName(),
                details.startsAt(), details.endsAt(), details.status(),
                details.cancellable(), details.reschedulable());
    }
}
