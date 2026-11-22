package ar.turnosodontologia.consultorio.shared.domain;

/** Un rango de consulta es invalido: faltan extremos, esta invertido o supera la amplitud maxima (HTTP 400). */
public class InvalidRangeException extends DomainException {

    public InvalidRangeException() {
        super("INVALID_RANGE");
    }
}
