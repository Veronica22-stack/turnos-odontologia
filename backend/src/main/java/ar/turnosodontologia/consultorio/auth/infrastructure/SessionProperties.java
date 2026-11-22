package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.shared.api.ConfigurationNames;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Configuración no sensible de la sesión, resuelta desde el {@link Environment}. El perfil {@code dev} fuerza una
 * cookie no segura para permitir HTTP local, sin relajar el resto de los atributos.
 */
record SessionProperties(int timeoutMinutes, String cookieName, boolean cookieSecure, List<String> allowedOrigins) {

    private static final int DEFAULT_TIMEOUT_MINUTES = 30;
    private static final String DEFAULT_COOKIE_NAME = "SESSIONID";

    static SessionProperties from(Environment environment) {
        boolean dev = environment.acceptsProfiles(Profiles.of(ConfigurationNames.DEV_PROFILE));
        int timeout = Integer.parseInt(environment.getProperty(
                ConfigurationNames.AUTH_SESSION_TIMEOUT_MIN, String.valueOf(DEFAULT_TIMEOUT_MINUTES)));
        String cookieName = environment.getProperty(ConfigurationNames.AUTH_SESSION_COOKIE_NAME, DEFAULT_COOKIE_NAME);
        boolean secure = !dev && Boolean.parseBoolean(
                environment.getProperty(ConfigurationNames.AUTH_SESSION_COOKIE_SECURE, "true"));
        List<String> origins = Arrays.stream(environment.getProperty(ConfigurationNames.CORS_ALLOWED_ORIGINS, "")
                        .split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return new SessionProperties(timeout, cookieName, secure, origins);
    }
}
