package ar.turnosodontologia.consultorio.auth.domain;

/** Política única de longitud de contraseñas, reutilizada por bootstrap y por el cambio de contraseña. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final String DEFAULT_FIELD = "newPassword";

    private PasswordPolicy() {
    }

    /** Valida sin mostrar el valor: el mensaje solo nombra el campo o la variable. */
    public static void requireValid(String raw) {
        requireValid(raw, DEFAULT_FIELD);
    }

    public static void requireValid(String raw, String fieldName) {
        if (raw == null || raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(fieldName + " debe tener entre "
                    + MIN_LENGTH + " y " + MAX_LENGTH + " caracteres");
        }
    }
}
