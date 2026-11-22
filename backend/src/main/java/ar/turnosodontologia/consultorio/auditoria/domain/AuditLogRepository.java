package ar.turnosodontologia.consultorio.auditoria.domain;

/** Puerto de escritura del rastro: solo se inserta, nunca se actualiza ni se borra. */
@FunctionalInterface
public interface AuditLogRepository {

    void save(AuditLogEntry entry);
}
