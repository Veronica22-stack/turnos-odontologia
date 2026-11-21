package ar.turnosodontologia.consultorio.shared.api;

import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Fabrica de respuestas de error {@code ProblemDetail}: titulo generico en espanol por estado, propiedad {@code code}
 * estable y NUNCA un {@code detail} dinamico (podria contener datos personales).
 */
public final class ProblemDetails {

    private static final Map<Integer, String> TITLES = Map.of(
            400, "Solicitud invalida",
            404, "Recurso no encontrado",
            405, "Metodo no permitido",
            406, "Formato de respuesta no aceptable",
            409, "Conflicto",
            415, "Tipo de contenido no soportado",
            422, "Regla de negocio incumplida",
            500, "Error interno");

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
