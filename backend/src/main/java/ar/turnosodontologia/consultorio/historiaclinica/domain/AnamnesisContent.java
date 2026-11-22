package ar.turnosodontologia.consultorio.historiaclinica.domain;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;

/**
 * Contenido de una version de anamnesis (PA-18, supuesto S-HC-08). Todos los campos de texto son opcionales y de hasta
 * 2000 caracteres, con al menos uno no vacio. Los campos desconocidos no existen en el record y por eso no se persisten.
 */
public record AnamnesisContent(
        int schemaVersion,
        String allergies,
        String medications,
        String medicalConditions,
        String habits,
        String notes) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int MAX_FIELD_LENGTH = 2000;

    public AnamnesisContent {
        allergies = normalize(allergies, "allergies");
        medications = normalize(medications, "medications");
        medicalConditions = normalize(medicalConditions, "medicalConditions");
        habits = normalize(habits, "habits");
        notes = normalize(notes, "notes");
        if (allergies == null && medications == null && medicalConditions == null && habits == null && notes == null) {
            throw new ValidationException("content", "AtLeastOneField");
        }
    }

    public static AnamnesisContent of(
            String allergies, String medications, String medicalConditions, String habits, String notes) {
        return new AnamnesisContent(
                CURRENT_SCHEMA_VERSION, allergies, medications, medicalConditions, habits, notes);
    }

    private static String normalize(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > MAX_FIELD_LENGTH) {
            throw new ValidationException(field, "Size");
        }
        return stripped;
    }

    /** El contenido es dato clinico: nunca se imprime (regla dura 6). */
    @Override
    public String toString() {
        return "AnamnesisContent[schemaVersion=" + schemaVersion + "]";
    }
}
