package ar.turnosodontologia.consultorio.auth.domain;

import java.util.regex.Pattern;

/**
 * Credenciales iniciales del DENTIST (variables BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD). Los mensajes de
 * error nombran la variable y nunca su valor; {@code toString} esta redactado (regla dura 6).
 */
public record BootstrapCredentials(String email, String password) {

    public static final String EMAIL_VARIABLE = "BOOTSTRAP_ADMIN_EMAIL";
    public static final String PASSWORD_VARIABLE = "BOOTSTRAP_ADMIN_PASSWORD";

    /** Largo minimo provisional (RN-AU-03, suposicion); la politica final es de C-04. */
    static final int MIN_PASSWORD_LENGTH = 12;

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static BootstrapCredentials of(String email, String password) {
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL_SHAPE.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException(
                    EMAIL_VARIABLE + " no tiene un formato de email valido (maximo " + MAX_EMAIL_LENGTH + " caracteres)");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    PASSWORD_VARIABLE + " debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        return new BootstrapCredentials(email.trim(), password);
    }

    @Override
    public String toString() {
        return "BootstrapCredentials[email=***, password=***]";
    }
}
