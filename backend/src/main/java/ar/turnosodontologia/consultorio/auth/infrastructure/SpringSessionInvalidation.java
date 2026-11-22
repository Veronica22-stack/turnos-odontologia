package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.application.SessionInvalidation;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Adaptador del puerto {@link SessionInvalidation} sobre el registro de sesiones de Spring Security (D8).
 *
 * <p>Solo compara identificadores opacos: no lee ni persiste email, DNI, teléfono ni datos clínicos.</p>
 */
@Component
public class SpringSessionInvalidation implements SessionInvalidation {

    private final SessionRegistry registry;

    public SpringSessionInvalidation(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void invalidateAllSessions(UUID userId) {
        expireSessionsNamed(userId.toString());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && userId.toString().equals(authentication.getName())) {
            HttpSession session = currentSession();
            if (session != null) {
                session.invalidate();
            }
        }
    }

    @Override
    public void invalidateCurrentSession() {
        HttpSession session = currentSession();
        if (session == null) {
            return;
        }
        SessionInformation information = registry.getSessionInformation(session.getId());
        if (information != null) {
            information.expireNow();
        }
        session.invalidate();
    }

    private void expireSessionsNamed(String name) {
        List<SessionInformation> sessions = registry.getAllPrincipals().stream()
                .filter(principal -> name.equals(principalName(principal)))
                .flatMap(principal -> registry.getAllSessions(principal, false).stream())
                .toList();
        sessions.forEach(SessionInformation::expireNow);
    }

    private static String principalName(Object principal) {
        if (principal instanceof Principal asPrincipal) {
            return asPrincipal.getName();
        }
        return principal.toString();
    }

    private static HttpSession currentSession() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes requestAttributes) {
            return requestAttributes.getRequest().getSession(false);
        }
        return null;
    }
}
