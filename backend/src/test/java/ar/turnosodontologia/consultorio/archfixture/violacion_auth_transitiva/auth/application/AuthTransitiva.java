package ar.turnosodontologia.consultorio.archfixture.violacion_auth_transitiva.auth.application;

import ar.turnosodontologia.consultorio.archfixture.violacion_auth_transitiva.auditoria.application.AuditoriaPuente;

/** Fixture: auth solo ve auditoria, pero auditoria alcanza un modulo de negocio prohibido. */
public final class AuthTransitiva {

    private final AuditoriaPuente auditoria;

    public AuthTransitiva(AuditoriaPuente auditoria) {
        this.auditoria = auditoria;
    }

    public void ejecutar() {
        auditoria.reportar();
    }
}
