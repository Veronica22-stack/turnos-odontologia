package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

/** Sesión expirada por invalidación remota: 401 en formato ProblemDetail, sin redirect ni Basic. */
@Component
class ProblemExpiredSessionHandler implements SessionInformationExpiredStrategy {

    private final ProblemResponseWriter writer;

    ProblemExpiredSessionHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException, ServletException {
        HttpSession session = event.getRequest().getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        writer.write(event.getResponse(), HttpStatus.UNAUTHORIZED, SecurityErrorCodes.UNAUTHENTICATED);
    }
}
