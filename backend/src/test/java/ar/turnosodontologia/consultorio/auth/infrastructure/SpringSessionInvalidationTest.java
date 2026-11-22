package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;
import ar.turnosodontologia.consultorio.auth.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SpringSessionInvalidationTest {

    private static final UUID SUBJECT = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private SessionRegistryImpl registry;
    private SpringSessionInvalidation invalidation;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistryImpl();
        invalidation = new SpringSessionInvalidation(registry);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void invalidaTodasLasSesionesDelUsuarioYNingunaDeOtro() {
        AuthenticatedUser subject = principal(SUBJECT);
        AuthenticatedUser other = principal(OTHER);
        registry.registerNewSession("s1", subject);
        registry.registerNewSession("s2", subject);
        registry.registerNewSession("o1", other);

        invalidation.invalidateAllSessions(SUBJECT);

        assertThat(registry.getAllSessions(subject, false)).allMatch(SessionInformation::isExpired);
        assertThat(registry.getAllSessions(other, false)).noneMatch(SessionInformation::isExpired);
    }

    @Test
    void reconoceComoPrincipalOpacoElIdentificadorEnTexto() {
        registry.registerNewSession("s1", SUBJECT.toString());

        invalidation.invalidateAllSessions(SUBJECT);

        assertThat(registry.getAllSessions(SUBJECT.toString(), false)).allMatch(SessionInformation::isExpired);
    }

    @Test
    void invalidateCurrentSessionCierraLaSesionHttpYExpiraSuRegistro() {
        AuthenticatedUser subject = principal(SUBJECT);
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(subject, null, java.util.List.of()));
        registry.registerNewSession(session.getId(), subject);

        invalidation.invalidateCurrentSession();

        assertThat(session.isInvalid()).isTrue();
        assertThat(registry.getAllSessions(subject, false)).allMatch(SessionInformation::isExpired);
    }

    @Test
    void invalidateCurrentSessionSinContextoEsIdempotente() {
        invalidation.invalidateCurrentSession();

        assertThat(registry.getAllPrincipals()).isEmpty();
    }

    private static AuthenticatedUser principal(UUID userId) {
        return new AuthenticatedUser(userId, UUID.randomUUID(), Role.DENTIST, false);
    }
}
