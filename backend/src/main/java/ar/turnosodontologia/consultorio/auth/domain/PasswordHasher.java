package ar.turnosodontologia.consultorio.auth.domain;

/** Puerto de hash de contrasenas; permite probar los casos de uso con un fake sin costo de CPU. */
public interface PasswordHasher {

    String hash(CharSequence raw);

    boolean matches(CharSequence raw, String hash);
}
