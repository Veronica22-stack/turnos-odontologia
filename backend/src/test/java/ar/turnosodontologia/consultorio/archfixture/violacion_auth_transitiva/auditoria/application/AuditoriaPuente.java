package ar.turnosodontologia.consultorio.archfixture.violacion_auth_transitiva.auditoria.application;

import ar.turnosodontologia.consultorio.archfixture.violacion_auth_transitiva.pacientes.api.PacientesApi;

/** Fixture: dependencia indirecta de auth hacia pacientes a traves de auditoria. */
public final class AuditoriaPuente {

    private final PacientesApi pacientes;

    public AuditoriaPuente(PacientesApi pacientes) {
        this.pacientes = pacientes;
    }

    public void reportar() {
        pacientes.consultar();
    }
}
