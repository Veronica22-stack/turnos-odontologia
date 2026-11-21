package ar.turnosodontologia.consultorio.auditoria.api;

/** Unico puerto publico de auditoria: los modulos de negocio solo reportan eventos por aqui (design D2/D5). */
public interface AuditLogger {

    void log(AuditEvent event);
}
