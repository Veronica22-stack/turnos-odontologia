package ar.turnosodontologia.consultorio.reservas.domain;

import java.security.SecureRandom;
import java.util.Base64;

/** Genera tokens CSPRNG de 256 bits en Base64URL sin padding (RN-RS-04). */
public final class BookingTokenGenerator {

    private static final int BYTES = 32;
    private final SecureRandom random;

    public BookingTokenGenerator() {
        this(new SecureRandom());
    }

    public BookingTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
