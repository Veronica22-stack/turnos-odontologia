package ar.turnosodontologia.consultorio.historiaclinica.infrastructure;

import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisContentResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.AnamnesisEntryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.api.OdontogramEntryResponse;
import ar.turnosodontologia.consultorio.historiaclinica.application.AnamnesisEntryData;
import ar.turnosodontologia.consultorio.historiaclinica.application.OdontogramEntryData;
import ar.turnosodontologia.consultorio.historiaclinica.application.RequestContext;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.shared.domain.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Utilidades compartidas por los controladores de historia clinica: actor, contexto de red y mapeo a DTOs. */
final class ClinicalRequestSupport {

    private static final int MAX_USER_AGENT = 512;

    private ClinicalRequestSupport() {
    }

    /** El nombre del principal de sesion es el id opaco de la cuenta (nunca el email). */
    static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String name = authentication != null ? authentication.getName() : null;
        if (name == null) {
            throw new ForbiddenException("FORBIDDEN");
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException error) {
            throw new ForbiddenException("FORBIDDEN");
        }
    }

    static RequestContext contextOf(HttpServletRequest request) {
        String agent = request.getHeader("User-Agent");
        if (agent != null && agent.length() > MAX_USER_AGENT) {
            agent = agent.substring(0, MAX_USER_AGENT);
        }
        return new RequestContext(request.getRemoteAddr(), agent);
    }

    static AnamnesisEntryResponse toResponse(AnamnesisEntryData data) {
        if (data == null) {
            return null;
        }
        AnamnesisContent content = data.content();
        return new AnamnesisEntryResponse(
                data.id(),
                data.version(),
                new AnamnesisContentResponse(
                        content.schemaVersion(), content.allergies(), content.medications(),
                        content.medicalConditions(), content.habits(), content.notes()),
                data.authorId(),
                data.createdAt(),
                data.correctsEntryId(),
                data.correctionReason(),
                data.correctedByEntryId());
    }

    static OdontogramEntryResponse toResponse(OdontogramEntryData data) {
        return new OdontogramEntryResponse(
                data.id(), data.toothCode(), data.surface() == null ? null : data.surface().name(),
                data.condition().name(), data.notes(), data.authorId(), data.createdAt(), data.correctsEntryId(),
                data.correctionReason(), data.correctedByEntryId());
    }
}
