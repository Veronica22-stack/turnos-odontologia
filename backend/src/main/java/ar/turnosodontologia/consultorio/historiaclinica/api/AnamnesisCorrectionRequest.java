package ar.turnosodontologia.consultorio.historiaclinica.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Correccion de una version de anamnesis: motivo obligatorio (hasta 500 caracteres) y el contenido corregido. */
public record AnamnesisCorrectionRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Valid AnamnesisRequest content) {

    /** Motivo y contenido son datos clinicos: nunca se imprimen (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisCorrectionRequest[***]";
    }
}
