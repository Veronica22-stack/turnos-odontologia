package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 con código estable: el de la regla de acceso o {@code CSRF_REQUIRED} si lo rechazó CSRF. */
@Component
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        Object reason = request.getAttribute(SecurityErrorCodes.ACCESS_DENIED_REASON);
        String code = reason instanceof String configured ? configured : csrfCodeOf(accessDeniedException);
        writer.write(response, HttpStatus.FORBIDDEN, code);
    }

    private static String csrfCodeOf(AccessDeniedException error) {
        String message = error.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("csrf")
                ? SecurityErrorCodes.CSRF_REQUIRED
                : SecurityErrorCodes.FORBIDDEN;
    }
}
