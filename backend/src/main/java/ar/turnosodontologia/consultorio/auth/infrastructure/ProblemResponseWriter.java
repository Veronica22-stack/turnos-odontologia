package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.ProblemDetails;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Escribe respuestas RFC 9457 desde filtros de seguridad, fuera del {@code @ControllerAdvice}. */
@Component
class ProblemResponseWriter {

    void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        String title = ProblemDetails.of(status, code).getTitle();
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":"
                + status.value() + ",\"code\":\"" + code + "\"}";
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
