package ar.turnosodontologia.consultorio.historiaclinica.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;

/**
 * Descripcion de una evolucion clinica: texto libre obligatorio, no en blanco y de hasta 5000 caracteres (igual que el
 * CHECK de la base). Se conserva tal cual, sin recortar.
 */
public record EvolutionDescription(String value) {

    public static final int MAX_LENGTH = 5000;

    public EvolutionDescription {
        if (value == null || value.isBlank()) {
            throw new ValidationException("description", "NotBlank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new ValidationException("description", "Size");
        }
    }

    public static EvolutionDescription of(String raw) {
        return new EvolutionDescription(raw);
    }

    /** La descripcion es dato clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionDescription[***]";
    }
}
