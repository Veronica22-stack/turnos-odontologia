package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Plantillas de texto plano en espanol (design D8). Reciben solo la prestacion, el horario, la zona y los datos del
 * consultorio: sin saludo con nombre, sin notas y sin datos personales del paciente. Sin HTML, para que ni el nombre
 * de la prestacion ni el del consultorio puedan inyectar marcado.
 */
public final class EmailTemplates {

    private static final Locale LOCALE = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", LOCALE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", LOCALE);
    private static final String SEPARATOR = " — ";

    private EmailTemplates() {
    }

    /**
     * Datos de una plantilla. {@code managementLink} es el link completo de gestion (o {@code null}); la direccion y
     * el telefono del consultorio pueden ser {@code null}.
     */
    public record Data(String serviceName, Instant startsAt, ZoneId zone, String practiceName,
            String practiceAddress, String practicePhone, String managementLink) {
    }

    /** Nombre de la plantilla (para registrar el envio sin registrar el contenido). */
    public static String templateName(NotificationType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static EmailMessage render(NotificationType type, Data data) {
        ZonedDateTime local = data.startsAt().atZone(data.zone());
        String when = DATE.format(local) + " " + TIME.format(local);
        return switch (type) {
            case CONFIRMATION -> new EmailMessage("Turno confirmado" + SEPARATOR + data.practiceName(),
                    appointmentBody("Tu turno quedó confirmado.", local, data, true));
            case REMINDER -> new EmailMessage("Recordatorio de turno" + SEPARATOR + data.practiceName(),
                    appointmentBody("Te recordamos tu próximo turno.", local, data, true));
            case RESCHEDULE -> new EmailMessage("Turno reprogramado" + SEPARATOR + data.practiceName(),
                    appointmentBody("Tu turno fue reprogramado. Estos son los nuevos datos.", local, data, true));
            case CANCELLATION -> new EmailMessage("Turno cancelado" + SEPARATOR + data.practiceName(),
                    appointmentBody("Tu turno fue cancelado.", local, data, false));
            case DENTIST_CANCELLATION -> new EmailMessage("Turno cancelado por un paciente" + SEPARATOR + when,
                    "Un paciente canceló su turno del " + when + " (" + data.serviceName() + ").\n");
        };
    }

    private static String appointmentBody(String intro, ZonedDateTime local, Data data, boolean withManagement) {
        StringBuilder body = new StringBuilder();
        body.append(intro).append("\n\n");
        body.append("Fecha: ").append(DATE.format(local)).append('\n');
        body.append("Hora: ").append(TIME.format(local)).append('\n');
        body.append("Prestación: ").append(data.serviceName()).append("\n\n");
        body.append(data.practiceName()).append('\n');
        if (data.practiceAddress() != null) {
            body.append("Dirección: ").append(data.practiceAddress()).append('\n');
        }
        if (data.practicePhone() != null) {
            body.append("Teléfono: ").append(data.practicePhone()).append('\n');
        }
        if (withManagement) {
            body.append('\n').append(managementBlock(data)).append('\n');
        }
        return body.toString();
    }

    private static String managementBlock(Data data) {
        if (data.managementLink() != null) {
            return "Para cancelar o reprogramar tu turno ingresá a: " + data.managementLink();
        }
        String phone = data.practicePhone() != null ? " al " + data.practicePhone() : "";
        return "Para cancelar o reprogramar, comunicate con el consultorio" + phone + ".";
    }
}
