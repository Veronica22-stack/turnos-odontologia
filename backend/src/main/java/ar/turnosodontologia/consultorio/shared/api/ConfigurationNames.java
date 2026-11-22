package ar.turnosodontologia.consultorio.shared.api;

/** Nombres de las variables de configuración de la sesión y del perfil local (sin valores). */
public final class ConfigurationNames {

    public static final String AUTH_SESSION_TIMEOUT_MIN = "AUTH_SESSION_TIMEOUT_MIN";
    public static final String AUTH_SESSION_COOKIE_NAME = "AUTH_SESSION_COOKIE_NAME";
    public static final String AUTH_SESSION_COOKIE_SECURE = "AUTH_SESSION_COOKIE_SECURE";
    public static final String CORS_ALLOWED_ORIGINS = "CORS_ALLOWED_ORIGINS";
    public static final String DEV_PROFILE = "dev";

    private ConfigurationNames() {
    }
}
