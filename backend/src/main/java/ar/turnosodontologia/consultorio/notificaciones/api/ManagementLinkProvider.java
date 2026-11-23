package ar.turnosodontologia.consultorio.notificaciones.api;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI opcional (design D11) con la que el modulo de reservas (C-14) aporta el token de gestion de un turno para el link
 * {@code APP_BASE_URL/turno/{token}} de los emails. Se invoca al enviar y se espera que emita un token guardado solo
 * hasheado (regla dura 8). Sin implementacion, o si devuelve vacio, el email omite el link e indica contactar al
 * consultorio. El token en claro nunca se persiste ni se loguea.
 */
public interface ManagementLinkProvider {

    Optional<String> tokenFor(UUID appointmentId, NotificationTypeView type);
}
