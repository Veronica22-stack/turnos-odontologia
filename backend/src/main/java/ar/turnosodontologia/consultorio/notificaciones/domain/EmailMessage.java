package ar.turnosodontologia.consultorio.notificaciones.domain;

/** Asunto y cuerpo de texto plano de un email, ya armados. */
public record EmailMessage(String subject, String body) {
}
