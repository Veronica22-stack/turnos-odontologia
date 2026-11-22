package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Contenido de una anamnesis: campos de texto opcionales de hasta 2000 caracteres con al menos uno no vacio. No hay
 * campos de autor, fecha, version ni historia clinica: el servidor los fija. Los campos desconocidos se ignoran.
 */
public record AnamnesisRequest(
        @Size(max = 2000) String allergies,
        @Size(max = 2000) String medications,
        @Size(max = 2000) String medicalConditions,
        @Size(max = 2000) String habits,
        @Size(max = 2000) String notes) {

    @AssertTrue
    public boolean isAnyFieldPresent() {
        return hasText(allergies) || hasText(medications) || hasText(medicalConditions) || hasText(habits)
                || hasText(notes);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** Contenido clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisRequest[***]";
    }
}
