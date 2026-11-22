package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothCondition;
import ar.turnosodontologia.consultorio.historiaclinica.domain.ToothPosition;

/** Estado de una pieza (o cara) a registrar; las notas son datos clinicos y no se imprimen. */
public record OdontogramEntryCommand(ToothPosition position, ToothCondition condition, String notes) {

    @Override
    public String toString() {
        return "OdontogramEntryCommand[***]";
    }
}
