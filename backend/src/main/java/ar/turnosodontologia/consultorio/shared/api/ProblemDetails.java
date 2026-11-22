package ar.turnosodontologia.consultorio.shared.api;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fabrica de respuestas de error {@code ProblemDetail}: titulo generico en espanol por estado, propiedad {@code code}
 * estable y NUNCA un {@code detail} dinamico (podria contener datos personales).
 */
public final class ProblemDetails {

    private static final Map<Integer, String> TITLES = Map.ofEntries(
            Map.entry(400, "Solicitud invalida"),
            Map.entry(401, "No autenticado"),
            Map.entry(403, "Acceso denegado"),
            Map.entry(404, "Recurso no encontrado"),
            Map.entry(405, "Metodo no permitido"),
            Map.entry(406, "Formato de respuesta no aceptable"),
            Map.entry(409, "Conflicto"),
            Map.entry(415, "Tipo de contenido no soportado"),
            Map.entry(422, "Regla de negocio incumplida"),
            Map.entry(429, "Demasiadas solicitudes"),
            Map.entry(500, "Error interno"));

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(TITLES.getOrDefault(status.value(), titleFor(status)));
        problem.setProperty("code", code);
        return problem;
    }

    private static String titleFor(HttpStatusCode status) {
        return status.is4xxClientError() ? "Solicitud invalida" : "Error interno";
    }
}
