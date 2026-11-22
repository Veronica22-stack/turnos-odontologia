package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Segunda barrera de autorización: exige sesión, rol DENTIST y sin cambio de contraseña pendiente (D6). */
@Component
class DentistAccessManager implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (current.getPrincipal() instanceof AuthenticatedUser user && user.mustChangePassword()) {
            context.getRequest().setAttribute(
                    SecurityErrorCodes.ACCESS_DENIED_REASON, SecurityErrorCodes.PASSWORD_CHANGE_REQUIRED);
            return new AuthorizationDecision(false);
        }
        boolean dentist = current.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_DENTIST".equals(authority.getAuthority()));
        if (!dentist) {
            context.getRequest().setAttribute(
                    SecurityErrorCodes.ACCESS_DENIED_REASON, SecurityErrorCodes.FORBIDDEN);
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(true);
    }
}
