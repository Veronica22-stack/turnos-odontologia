package ar.turnosodontologia.consultorio.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Valores por defecto y por perfil de la sesion (spec runtime-configuration). */
class SessionPropertiesTest {

    @Test
    void produccionUsaCookieSeguraNombreSessionIdYTreintaMinutos() {
        MockEnvironment environment = new MockEnvironment();

        SessionProperties properties = SessionProperties.from(environment);

        assertThat(properties.timeoutMinutes()).isEqualTo(30);
        assertThat(properties.cookieName()).isEqualTo("SESSIONID");
        assertThat(properties.cookieSecure()).isTrue();
    }

    @Test
    void perfilDevFuerzaCookieNoSeguraSinRelajarElNombreNiElPlazo() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        environment.setProperty("AUTH_SESSION_COOKIE_SECURE", "true");

        SessionProperties properties = SessionProperties.from(environment);

        assertThat(properties.cookieSecure()).isFalse();
        assertThat(properties.cookieName()).isEqualTo("SESSIONID");
        assertThat(properties.timeoutMinutes()).isEqualTo(30);
    }

    @Test
    void produccionRespetaValoresExplicitosNoSensibles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("AUTH_SESSION_TIMEOUT_MIN", "5");
        environment.setProperty("AUTH_SESSION_COOKIE_NAME", "OTRA_SESSION");
        environment.setProperty("AUTH_SESSION_COOKIE_SECURE", "false");
        environment.setProperty("CORS_ALLOWED_ORIGINS", "https://consultorio.example.ar,https://otro.example.ar");

        SessionProperties properties = SessionProperties.from(environment);

        assertThat(properties.timeoutMinutes()).isEqualTo(5);
        assertThat(properties.cookieName()).isEqualTo("OTRA_SESSION");
        assertThat(properties.cookieSecure()).isFalse();
        assertThat(properties.allowedOrigins())
                .containsExactly("https://consultorio.example.ar", "https://otro.example.ar");
    }
}
