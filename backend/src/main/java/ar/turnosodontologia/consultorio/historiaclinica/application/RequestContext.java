package ar.turnosodontologia.consultorio.historiaclinica.application;

/** Contexto de red de la solicitud (IP y agente) que se pasa a la auditoria; son metadatos, no datos clinicos. */
public record RequestContext(String ip, String userAgent) {

    public static RequestContext none() {
        return new RequestContext(null, null);
    }
}
