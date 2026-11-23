package ar.turnosodontologia.consultorio.notificaciones.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de dominio por el que el despacho pide el token de gestion de un turno (design D11). La SPI publica
 * ({@code notificaciones.api.ManagementLinkProvider}) la adapta infrastructure; {@code application} no puede usar la
 * api de su propio modulo.
 */
public interface ManagementTokenSource {

    /** {@code true} si hay un emisor de tokens; sin emisor el email omite el link. */
    boolean enabled();

    Optional<String> tokenFor(UUID appointmentId, NotificationType type);

    /** Sin emisor de tokens (estado de C-13). */
    static ManagementTokenSource none() {
        return new ManagementTokenSource() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public Optional<String> tokenFor(UUID appointmentId, NotificationType type) {
                return Optional.empty();
            }
        };
    }
}
