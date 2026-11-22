package ar.turnosodontologia.consultorio.historiaclinica.domain;

/** Tipos de entrada clinica; C-17 (odontograma) y C-18 (evoluciones) agregan sus constantes. */
public enum ClinicalEntryType {
    ANAMNESIS("anamnesis", "anamnesis_entry"),
    ODONTOGRAM("odontogram", "odontogram_entry");

    private final String pathSegment;
    private final String auditEntityType;

    ClinicalEntryType(String pathSegment, String auditEntityType) {
        this.pathSegment = pathSegment;
        this.auditEntityType = auditEntityType;
    }

    /** Segmento de ruta REST: {@code /clinical-record/{pathSegment}/{entryId}/corrections}. */
    public String pathSegment() {
        return pathSegment;
    }

    /** Valor de {@code entity_type} en el rastro de auditoria. */
    public String auditEntityType() {
        return auditEntityType;
    }
}
