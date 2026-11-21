package ar.turnosodontologia.consultorio.auditoria.application;

import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogCommand;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogEntry;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogRepository;
import ar.turnosodontologia.consultorio.auditoria.domain.SensitiveDataSanitizer;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Completa el evento con la instalacion y el instante del servidor, sanitiza los detalles y persiste. No conoce la
 * api ni la infraestructura de su modulo (regla de arquitectura `applicationIndependent`).
 */
@Service
public final class AuditLoggerService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLoggerService.class);

    private final AuditLogRepository repository;
    private final InstallationPractice practice;
    private final Clock clock;

    public AuditLoggerService(AuditLogRepository repository, InstallationPractice practice, Clock clock) {
        this.repository = repository;
        this.practice = practice;
        this.clock = clock;
    }

    /**
     * Registra el evento; solo devuelve cuando el rastro quedo persistido. Si falla, se propaga un error generico
     * (fail-closed, D4): ni el mensaje de la base ni los detalles llegan a la respuesta ni al log.
     */
    public void record(AuditLogCommand command) {
        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(command.details());
        AuditLogEntry entry = new AuditLogEntry(
                practice.id(),
                command.action(),
                command.userId(),
                command.patientId(),
                command.entityType(),
                command.entityId(),
                command.ip(),
                command.userAgent(),
                clock.instant(),
                sanitized);
        try {
            repository.save(entry);
        } catch (RuntimeException error) {
            String errorId = UUID.randomUUID().toString();
            logger.error("AUDIT_WRITE_FAILED errorId={} origin={}", errorId, error.getClass().getSimpleName());
            throw new BusinessRuleException("AUDIT_WRITE_FAILED");
        }
    }
}
