package ar.turnosodontologia.consultorio.historiaclinica.api;

/** Contenido de una version de anamnesis tal como se guardo. */
public record AnamnesisContentResponse(
        int schemaVersion,
        String allergies,
        String medications,
        String medicalConditions,
        String habits,
        String notes) {

    /** Contenido clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisContentResponse[schemaVersion=" + schemaVersion + "]";
    }
}
