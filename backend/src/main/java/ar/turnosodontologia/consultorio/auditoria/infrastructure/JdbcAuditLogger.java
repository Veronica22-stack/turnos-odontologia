package ar.turnosodontologia.consultorio.auditoria.infrastructure;

import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.auditoria.application.AuditLoggerService;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogCommand;
import org.springframework.stereotype.Component;

/**
 * Adaptador del puerto publico: traduce el evento a un comando de dominio y delega en el servicio, que fija la
 * instalacion, el instante y la sanitizacion (design D2/D4).
 */
@Component
final class JdbcAuditLogger implements AuditLogger {

    private final AuditLoggerService service;

    JdbcAuditLogger(AuditLoggerService service) {
        this.service = service;
    }

    @Override
    public void log(AuditEvent event) {
        service.record(new AuditLogCommand(
                event.action().name(),
                event.userId(),
                event.patientId(),
                event.entityType(),
                event.entityId(),
                event.ip(),
                event.userAgent(),
                event.details()));
    }
}
