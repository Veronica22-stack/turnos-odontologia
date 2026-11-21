package ar.turnosodontologia.consultorio.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Cuenta de acceso al sistema. El email se guarda normalizado (sin espacios y en minusculas). */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, updatable = false)
    private Role role;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected AppUser() {
        // Requerido por JPA.
    }

    private AppUser(UUID practiceId, String email, String passwordHash, Role role, Instant createdAt) {
        this.practiceId = practiceId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    /** Odontologo nuevo: debe cambiar la contrasena en su primer ingreso. El id lo asigna Hibernate (UUID v7). */
    public static AppUser newDentist(UUID practiceId, String email, String passwordHash, Clock clock) {
        AppUser user = new AppUser(
                Objects.requireNonNull(practiceId),
                normalize(email),
                Objects.requireNonNull(passwordHash),
                Role.DENTIST,
                clock.instant());
        user.mustChangePassword = true;
        return user;
    }

    /** Forma canonica del email: sin espacios alrededor y en minusculas. */
    public static String normalize(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public Role role() {
        return role;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Optional<Instant> lockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> lastLoginAt() {
        return Optional.ofNullable(lastLoginAt);
    }

    /** Solo el id y el rol: nunca el email ni el hash (reglas duras 6 y 8). */
    @Override
    public String toString() {
        return "AppUser[id=" + id + ", role=" + role + "]";
    }
}
