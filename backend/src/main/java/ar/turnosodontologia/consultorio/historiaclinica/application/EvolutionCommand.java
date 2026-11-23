package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.EvolutionDescription;
import java.util.UUID;

/** Alta de una evolucion: descripcion ya validada y turno opcional. */
public record EvolutionCommand(EvolutionDescription description, UUID appointmentId) {

    /** La descripcion es dato clinico: solo se informa si hay turno asociado (regla dura 6). */
    @Override
    public String toString() {
        return "EvolutionCommand[withAppointment=" + (appointmentId != null) + "]";
    }
}
