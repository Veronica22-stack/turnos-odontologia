package ar.turnosodontologia.consultorio.archfixture.ok_auth.auth.application;

import ar.turnosodontologia.consultorio.archfixture.ok_auth.auditoria.api.AuditoriaApiOk;

/** Fixture positivo: auth.application → auditoria.api es una arista declarada (design D11). */
public final class RegistraAcceso {

    private final AuditoriaApiOk auditoria;

    public RegistraAcceso(AuditoriaApiOk auditoria) {
        this.auditoria = auditoria;
    }

    public void ejecutar() {
        auditoria.reportar("LOGIN_OK");
    }
}
