package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.Objects;

/** Posicion del odontograma: una pieza completa (sin cara) o una cara de pieza. Clave de la derivacion del estado. */
public record ToothPosition(ToothCode tooth, ToothSurface surface) {

    public ToothPosition {
        Objects.requireNonNull(tooth, "tooth");
    }

    public boolean hasSurface() {
        return surface != null;
    }
}
