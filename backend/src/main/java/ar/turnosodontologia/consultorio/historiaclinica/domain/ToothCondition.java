package ar.turnosodontologia.consultorio.historiaclinica.domain;

/** Estados del catalogo base del odontograma; solo caries y obturacion pueden referirse a una cara. */
public enum ToothCondition {
    HEALTHY(false),
    CARIES(true),
    FILLED(true),
    MISSING(false),
    CROWN(false),
    ROOT_CANAL(false);

    private final boolean allowsSurface;

    ToothCondition(boolean allowsSurface) {
        this.allowsSurface = allowsSurface;
    }

    public boolean allowsSurface() {
        return allowsSurface;
    }
}
