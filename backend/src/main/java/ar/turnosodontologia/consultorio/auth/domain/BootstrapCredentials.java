package ar.turnosodontologia.consultorio.auth.domain;

import java.util.regex.Pattern;

/**
 * Credenciales iniciales del DENTIST (variables BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD). Los mensajes de
 * error nombran la variable y nunca su valor; {@code toString} esta redactado (regla dura 6).
 */
public record BootstrapCredentials(String email, String password) {

    public static final String EMAIL_VARIABLE = "BOOTSTRAP_ADMIN_EMAIL";
    public static final String PASSWORD_VARIABLE = "BOOTSTRAP_ADMIN_PASSWORD";

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final Pattern EMAIL_SHAPE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static BootstrapCredentials of(String email, String password) {
        if (email == null || email.length() > MAX_EMAIL_LENGTH || !EMAIL_SHAPE.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException(
                    EMAIL_VARIABLE + " no tiene un formato de email valido (maximo " + MAX_EMAIL_LENGTH + " caracteres)");
        }
        PasswordPolicy.requireValid(password, PASSWORD_VARIABLE);
        return new BootstrapCredentials(email.trim(), password);
    }

    @Override
    public String toString() {
        return "BootstrapCredentials[email=***, password=***]";
    }
}
