package ar.turnosodontologia.consultorio.shared.domain;

/** La hora local pedida no existe en la zona (salto de horario de verano). */
public class NonexistentLocalTimeException extends RuntimeException {

    public NonexistentLocalTimeException(String message) {
        super(message);
    }
}
