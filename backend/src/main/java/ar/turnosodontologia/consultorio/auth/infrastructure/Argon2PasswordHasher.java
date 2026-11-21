package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.domain.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;

/** {@link PasswordHasher} respaldado por un {@link PasswordEncoder} (Argon2id, ver {@link PasswordConfiguration}). */
class Argon2PasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    Argon2PasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(CharSequence raw) {
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(CharSequence raw, String hash) {
        return encoder.matches(raw, hash);
    }
}
