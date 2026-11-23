package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.api.ManagementLinkProvider;
import ar.turnosodontologia.consultorio.notificaciones.api.NotificationTypeView;
import ar.turnosodontologia.consultorio.notificaciones.domain.ManagementTokenSource;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Adapta la SPI publica opcional {@link ManagementLinkProvider} al puerto de dominio (design D11). */
@Component
class ManagementLinkProviderAdapter implements ManagementTokenSource {

    private final ObjectProvider<ManagementLinkProvider> provider;

    ManagementLinkProviderAdapter(ObjectProvider<ManagementLinkProvider> provider) {
        this.provider = provider;
    }

    @Override
    public boolean enabled() {
        return provider.getIfAvailable() != null;
    }

    @Override
    public Optional<String> tokenFor(UUID appointmentId, NotificationType type) {
        ManagementLinkProvider current = provider.getIfAvailable();
        if (current == null) {
            return Optional.empty();
        }
        return current.tokenFor(appointmentId, NotificationTypeView.valueOf(type.name()));
    }
}
