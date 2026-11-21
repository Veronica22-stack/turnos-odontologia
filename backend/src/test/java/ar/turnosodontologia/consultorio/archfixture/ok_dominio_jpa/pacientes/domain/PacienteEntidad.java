package ar.turnosodontologia.consultorio.archfixture.ok_dominio_jpa.pacientes.domain;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Clase fixture para verificar reglas de arquitectura; no es codigo de produccion. */
// MappedSuperclass y no Entity: el fixture vive bajo el paquete de la aplicacion y una @Entity real seria escaneada
// por Hibernate (ddl-auto=validate exigiria su tabla).
@MappedSuperclass
public class PacienteEntidad {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
}
