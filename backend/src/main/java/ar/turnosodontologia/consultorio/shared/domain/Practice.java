package ar.turnosodontologia.consultorio.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Consultorio de la instalacion (single-tenant v1). La fila la siembra la migracion V2; la aplicacion no la crea ni
 * la borra (el rol de aplicacion no tiene INSERT/DELETE sobre la tabla).
 */
@Entity
@Table(name = "practice")
public class Practice {

    private static final Set<Integer> ALLOWED_GRANULARITIES = Set.of(10, 15, 20, 30, 60);

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address")
    private String address;

    @Column(name = "slot_granularity_min", nullable = false)
    private int slotGranularityMin = 15;

    @Column(name = "min_notice_min", nullable = false)
    private int minNoticeMin = 120;

    @Column(name = "max_advance_days", nullable = false)
    private int maxAdvanceDays = 30;

    @Column(name = "reminder_hours_before", nullable = false)
    private int reminderHoursBefore = 24;

    protected Practice() {
        // Requerido por JPA.
    }

    private Practice(
            UUID id,
            String name,
            String timezone,
            String contactEmail,
            String contactPhone,
            String address,
            int slotGranularityMin,
            int minNoticeMin,
            int maxAdvanceDays,
            int reminderHoursBefore) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.slotGranularityMin = slotGranularityMin;
        this.minNoticeMin = minNoticeMin;
        this.maxAdvanceDays = maxAdvanceDays;
        this.reminderHoursBefore = reminderHoursBefore;
    }

    /** Reconstruye un consultorio basico (compatibilidad con tests existentes). */
    public static Practice existing(UUID id, String name, String timezone) {
        return new Practice(id, name, timezone, null, null, null, 15, 120, 30, 24);
    }

    /** Reconstruye un consultorio con todos los atributos. */
    public static Practice existing(
            UUID id,
            String name,
            String timezone,
            String contactEmail,
            String contactPhone,
            String address,
            int slotGranularityMin,
            int minNoticeMin,
            int maxAdvanceDays,
            int reminderHoursBefore) {
        return new Practice(
                id,
                name,
                timezone,
                contactEmail,
                contactPhone,
                address,
                slotGranularityMin,
                minNoticeMin,
                maxAdvanceDays,
                reminderHoursBefore);
    }

    public void updateSettings(
            String name,
            String timezone,
            String contactEmail,
            String contactPhone,
            String address,
            int slotGranularityMin,
            int minNoticeMin,
            int maxAdvanceDays,
            int reminderHoursBefore) {
        validateName(name);
        validateTimezone(timezone);
        validateContactEmail(contactEmail);
        validateContactPhone(contactPhone);
        validateAddress(address);
        validateBookingParameters(slotGranularityMin, minNoticeMin, maxAdvanceDays, reminderHoursBefore);

        this.name = name.trim();
        this.timezone = timezone.trim();
        this.contactEmail = contactEmail != null && !contactEmail.isBlank() ? contactEmail.trim() : null;
        this.contactPhone = contactPhone != null && !contactPhone.isBlank() ? contactPhone.trim() : null;
        this.address = address != null && !address.isBlank() ? address.trim() : null;
        this.slotGranularityMin = slotGranularityMin;
        this.minNoticeMin = minNoticeMin;
        this.maxAdvanceDays = maxAdvanceDays;
        this.reminderHoursBefore = reminderHoursBefore;
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 200) {
            throw new ValidationException("name", "name debe ser no vacio y de hasta 200 caracteres");
        }
    }

    private static void validateTimezone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new ValidationException("timezone", "timezone debe ser no vacio");
        }
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            throw new ValidationException("timezone", "timezone no es un identificador de zona horaria valido");
        }
    }

    private static void validateContactEmail(String email) {
        if (email != null && email.length() > 254) {
            throw new ValidationException("contact_email", "contact_email no puede superar 254 caracteres");
        }
    }

    private static void validateContactPhone(String phone) {
        if (phone != null && phone.length() > 50) {
            throw new ValidationException("contact_phone", "contact_phone no puede superar 50 caracteres");
        }
    }

    private static void validateAddress(String address) {
        if (address != null && address.length() > 300) {
            throw new ValidationException("address", "address no puede superar 300 caracteres");
        }
    }

    private static void validateBookingParameters(
            int slotGranularityMin, int minNoticeMin, int maxAdvanceDays, int reminderHoursBefore) {
        if (!ALLOWED_GRANULARITIES.contains(slotGranularityMin)) {
            throw new ValidationException("slot_granularity_min", "slot_granularity_min debe ser uno de: 10, 15, 20, 30, 60");
        }
        if (minNoticeMin < 0) {
            throw new ValidationException("min_notice_min", "min_notice_min no puede ser negativo");
        }
        if (maxAdvanceDays < 1 || maxAdvanceDays > 365) {
            throw new ValidationException("max_advance_days", "max_advance_days debe estar entre 1 y 365");
        }
        if (reminderHoursBefore < 1 || reminderHoursBefore > 168) {
            throw new ValidationException("reminder_hours_before", "reminder_hours_before debe estar entre 1 y 168");
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String timezone() {
        return timezone;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public String contactPhone() {
        return contactPhone;
    }

    public String address() {
        return address;
    }

    public int slotGranularityMin() {
        return slotGranularityMin;
    }

    public int minNoticeMin() {
        return minNoticeMin;
    }

    public int maxAdvanceDays() {
        return maxAdvanceDays;
    }

    public int reminderHoursBefore() {
        return reminderHoursBefore;
    }
}
