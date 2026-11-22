package ar.turnosodontologia.consultorio.auth.application;

import ar.turnosodontologia.consultorio.auth.domain.AuthenticatedUser;

/** Puerto de salida de sesión: la infraestructura HTTP decide dónde vivo el contexto autenticado. */
public interface LoginSession {

    void establish(AuthenticatedUser user);
}
