package ar.turnosodontologia.consultorio.archfixture.ok_auth.auditoria.api;

/** Fixture: cara publica de auditoria que el modulo auth puede usar. */
public interface AuditoriaApiOk {

    void reportar(String evento);
}
