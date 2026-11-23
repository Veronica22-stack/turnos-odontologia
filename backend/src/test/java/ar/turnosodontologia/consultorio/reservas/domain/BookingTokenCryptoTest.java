package ar.turnosodontologia.consultorio.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests puros de generación, hash y estado del token (RN-RS-04/06, regla dura 8). */
class BookingTokenCryptoTest {

    @Test
    void generadorProduceTokensBase64UrlDe256BitsYDiferentes() {
        BookingTokenGenerator generator = new BookingTokenGenerator();
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String token = generator.generate();
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(20);
    }

    @Test
    void hashEsSha256HexDe64CaracteresYEstable() {
        String hash = TokenHasher.hash("token-de-prueba");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(TokenHasher.hash("token-de-prueba")).isEqualTo(hash);
        assertThat(hash).doesNotContain("token-de-prueba");
        assertThatThrownBy(() -> TokenHasher.hash("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void comparacionEnTiempoConstanteAceptaIgualesYRechazaDistintos() {
        String raw = "abc123-def456_GHI789";
        String hash = TokenHasher.hash(raw);
        assertThat(TokenHasher.matches(raw, hash)).isTrue();
        assertThat(TokenHasher.matches(raw + "x", hash)).isFalse();
        assertThat(TokenHasher.matches(raw, "0".repeat(64))).isFalse();
        assertThat(TokenHasher.matches(null, hash)).isFalse();
        assertThat(TokenHasher.matches(raw, null)).isFalse();
    }

    @Test
    void tokenSoloEsUsableAntesDeExpirarYSinRevocar() {
        Instant now = Instant.parse("2026-12-01T12:00:00Z");
        BookingToken token = BookingToken.create(java.util.UUID.randomUUID(), "a".repeat(64),
                now.plusSeconds(60), now, "PRIVACY_NOTICE_V1", now);
        assertThat(token.isUsable(now)).isTrue();
        assertThat(token.isUsable(now.plusSeconds(60))).isFalse();
        token.revoke(now);
        assertThat(token.isUsable(now)).isFalse();
        assertThat(token.revokedAt()).isEqualTo(now);
    }

    @Test
    void extenderVigenciaSoloLaAumenta() {
        Instant now = Instant.parse("2026-12-01T12:00:00Z");
        BookingToken token = BookingToken.forEmail(java.util.UUID.randomUUID(), "b".repeat(64),
                now.plusSeconds(60), now);
        token.extendExpiration(now.plusSeconds(30));
        assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(60));
        token.extendExpiration(now.plusSeconds(120));
        assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(120));
    }
}
