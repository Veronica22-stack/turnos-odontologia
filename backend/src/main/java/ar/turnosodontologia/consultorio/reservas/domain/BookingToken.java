package ar.turnosodontologia.consultorio.reservas.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Token de gestión de un turno. El token en claro nunca se persiste: solo su hash SHA-256 (regla dura 8). */
@Entity
@Table(name = "booking_token")
public class BookingToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "appointment_id", nullable = false, updatable = false)
    private UUID appointmentId;

    @Column(name = "token_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "privacy_notice_version", nullable = false)
    private String privacyNoticeVersion;

    @Column(name = "privacy_accepted_at", nullable = false)
    private Instant privacyAcceptedAt;

    protected BookingToken() {
        // JPA
    }

    private BookingToken(UUID appointmentId, String tokenHash, Instant expiresAt, Instant now,
            String privacyNoticeVersion, Instant privacyAcceptedAt) {
        this.appointmentId = appointmentId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.privacyNoticeVersion = privacyNoticeVersion;
        this.privacyAcceptedAt = privacyAcceptedAt;
    }

    public static BookingToken create(UUID appointmentId, String tokenHash, Instant expiresAt, Instant now,
            String privacyNoticeVersion, Instant privacyAcceptedAt) {
        if (appointmentId == null || tokenHash == null || expiresAt == null || privacyNoticeVersion == null
                || privacyAcceptedAt == null) {
            throw new IllegalArgumentException("booking token: required fields");
        }
        return new BookingToken(appointmentId, tokenHash, expiresAt, now, privacyNoticeVersion, privacyAcceptedAt);
    }

    /** Token para enlaces de correo: sin consentimiento de reserva (no aplica). */
    public static BookingToken forEmail(UUID appointmentId, String tokenHash, Instant expiresAt, Instant now) {
        return new BookingToken(appointmentId, tokenHash, expiresAt, now, "EMAIL_LINK", now);
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && now != null && now.isBefore(expiresAt);
    }

    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    public void extendExpiration(Instant newExpiresAt) {
        if (newExpiresAt != null && newExpiresAt.isAfter(this.expiresAt)) {
            this.expiresAt = newExpiresAt;
        }
    }

    public UUID id() {
        return id;
    }

    public UUID appointmentId() {
        return appointmentId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
