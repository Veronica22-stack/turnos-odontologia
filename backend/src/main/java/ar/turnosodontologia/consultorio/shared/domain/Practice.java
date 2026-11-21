package ar.turnosodontologia.consultorio.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Consultorio de la instalacion (single-tenant v1). La fila la siembra la migracion V2; la aplicacion no la crea ni
 * la borra (el rol de aplicacion no tiene INSERT/DELETE sobre la tabla).
 */
@Entity
@Table(name = "practice")
public class Practice {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "timezone", nullable = false)
    private String timezone;

    protected Practice() {
        // Requerido por JPA.
    }

    private Practice(UUID id, String name, String timezone) {
        this.id = id;
        this.name = name;
        this.timezone = timezone;
    }

    /** Reconstruye un consultorio ya existente (por ejemplo en tests); no sirve para crear filas nuevas. */
    public static Practice existing(UUID id, String name, String timezone) {
        return new Practice(id, name, timezone);
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
}
