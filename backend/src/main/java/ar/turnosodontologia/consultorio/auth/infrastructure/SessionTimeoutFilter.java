package ar.turnosodontologia.consultorio.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Renueva o rechaza la sesión según el reloj del servidor y el plazo configurable (D1). */
@Component
class SessionTimeoutFilter extends OncePerRequestFilter {

    private final Clock clock;
    private final SessionProperties properties;
    private final ProblemResponseWriter writer;

    SessionTimeoutFilter(Clock clock, SessionProperties properties, ProblemResponseWriter writer) {
        this.clock = clock;
        this.properties = properties;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (authentication != null && authentication.isAuthenticated() && session != null) {
            if (SessionTimeout.isExpired(session, clock.instant())) {
                session.invalidate();
                SecurityContextHolder.clearContext();
                writer.write(response, HttpStatus.UNAUTHORIZED, SecurityErrorCodes.UNAUTHENTICATED);
                return;
            }
            SessionTimeout.schedule(session, clock, properties.timeoutMinutes());
        }
        chain.doFilter(request, response);
    }
}
