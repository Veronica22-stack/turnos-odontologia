package ar.turnosodontologia.consultorio.notificaciones.domain;

/** Puerto de envio de emails; los adaptadores (log, smtp) viven en infrastructure. */
@FunctionalInterface
public interface EmailSender {

    /** @throws EmailSendException si el envio falla, con un codigo del catalogo y si conviene reintentar */
    void send(OutgoingEmail email);
}
