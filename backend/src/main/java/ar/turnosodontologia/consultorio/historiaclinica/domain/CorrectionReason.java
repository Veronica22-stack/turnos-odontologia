package ar.turnosodontologia.consultorio.historiaclinica.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;

/** Motivo de una correccion clinica: obligatorio, recortado y de hasta 500 caracteres (igual que el CHECK de la base). */
public record CorrectionReason(String value) {

    public static final int MAX_LENGTH = 500;

    public CorrectionReason {
        if (value == null || value.isBlank()) {
            throw new ValidationException("reason", "NotBlank");
        }
        value = value.strip();
        if (value.length() > MAX_LENGTH) {
            throw new ValidationException("reason", "Size");
        }
    }

    public static CorrectionReason of(String raw) {
        return new CorrectionReason(raw);
    }

    /** El motivo es dato clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "CorrectionReason[***]";
    }
}
