package ar.turnosodontologia.consultorio.pacientes.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "practice_id", nullable = false, updatable = false)
    private UUID practiceId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "dni", nullable = false, updatable = false)
    private String dni;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "address")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, updatable = false)
    private PatientSource source;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Patient() {
        // JPA
    }

    private Patient(
            UUID id,
            UUID practiceId,
            String firstName,
            String lastName,
            String dni,
            String phone,
            String email,
            LocalDate birthDate,
            String address,
            PatientSource source,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.practiceId = practiceId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dni = dni;
        this.phone = phone;
        this.email = email;
        this.birthDate = birthDate;
        this.address = address;
        this.source = source;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Patient create(
            UUID practiceId,
            String firstName,
            String lastName,
            String dni,
            String phone,
            String email,
            LocalDate birthDate,
            String address,
            PatientSource source) {
        if (practiceId == null) {
            throw new ValidationException("practice_id", "practice_id must not be null");
        }
        String validFirst = validateRequired(firstName, "first_name");
        String validLast = validateRequired(lastName, "last_name");
        String validDni = normalizeAndValidateDni(dni);
        String validPhone = validateRequired(phone, "phone");
        String cleanEmail = normalizeEmail(email);
        String cleanAddress = address != null ? address.trim() : null;
        PatientSource validSource = source != null ? source : PatientSource.MANUAL;
        Instant now = Instant.now();

        return new Patient(
                null,
                practiceId,
                validFirst,
                validLast,
                validDni,
                validPhone,
                cleanEmail,
                birthDate,
                cleanAddress,
                validSource,
                true,
                now,
                now);
    }

    public void update(
            String firstName,
            String lastName,
            String phone,
            String email,
            LocalDate birthDate,
            String address) {
        this.firstName = validateRequired(firstName, "first_name");
        this.lastName = validateRequired(lastName, "last_name");
        this.phone = validateRequired(phone, "phone");
        this.email = normalizeEmail(email);
        this.birthDate = birthDate;
        this.address = address != null ? address.trim() : null;
        this.updatedAt = Instant.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    private static String validateRequired(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new ValidationException(field, field + " must not be blank");
        }
        return value.trim();
    }

    public static String normalizeAndValidateDni(String rawDni) {
        if (rawDni == null) {
            throw new ValidationException("dni", "dni must not be null");
        }
        String clean = rawDni.replaceAll("[^0-9]", "");
        if (clean.length() < 6 || clean.length() > 9) {
            throw new ValidationException("dni", "dni length must be between 6 and 9 digits");
        }
        return clean;
    }

    private static String normalizeEmail(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return null;
        }
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public UUID id() {
        return id;
    }

    public UUID practiceId() {
        return practiceId;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String dni() {
        return dni;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public String address() {
        return address;
    }

    public PatientSource source() {
        return source;
    }

    public boolean isActive() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
