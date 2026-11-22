package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.application.LoginSession;
import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Establece el contexto autenticado en la {@code HttpSession}, rotando el id antes de guardarlo (D1/D2). */
@Component
class HttpLoginSession implements LoginSession {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final SecurityContextRepository securityContexts;
    private final SessionRegistry registry;
    private final Clock clock;
    private final SessionProperties properties;

    HttpLoginSession(HttpServletRequest request, HttpServletResponse response,
            SecurityContextRepository securityContexts, SessionRegistry registry, Clock clock,
            SessionProperties properties) {
        this.request = request;
        this.response = response;
        this.securityContexts = securityContexts;
        this.registry = registry;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    public void establish(AuthenticatedUser user) {
        HttpSession initial = request.getSession(true);
        String previousId = initial.getId();
        request.changeSessionId();
        HttpSession session = request.getSession(false);
        registry.removeSessionInformation(previousId);
        registry.registerNewSession(session.getId(), user);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);

        SessionTimeout.schedule(session, clock, properties.timeoutMinutes());
    }
}
