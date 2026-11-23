package ar.turnosodontologia.consultorio.notificaciones.application;

/**
 * Pide un despacho inmediato de lo vencido (design D14). La implementacion nunca bloquea al llamador: la latencia del
 * proveedor de email no debe afectar la respuesta HTTP que origino el evento.
 */
@FunctionalInterface
public interface DispatchTrigger {

    void requestDispatch();
}
