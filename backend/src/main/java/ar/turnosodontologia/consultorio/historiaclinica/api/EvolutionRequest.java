package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta de una evolucion: descripcion obligatoria (hasta 5000 caracteres) y turno opcional. El turno llega como texto
 * validado por {@code @Pattern} para que un valor mal formado produzca {@code 400 VALIDATION_FAILED} con el campo y
 * sin eco. No hay id, autor ni fecha: el servidor los fija. Los campos desconocidos se ignoran.
 */
public record EvolutionRequest(
        @NotBlank @Size(max = 5000) String description,
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        String appointmentId) {

    /** La descripcion es dato clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionRequest[***]";
    }
}
