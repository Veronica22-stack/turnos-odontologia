package ar.turnosodontologia.consultorio.archfixture.violacion_auth.auth.api;

import ar.turnosodontologia.consultorio.archfixture.violacion_auth.pacientes.api.PacientesApi;

/** Fixture negativo: auth.api → pacientes.api debe fallar nombrando origen y destino. */
public final class AuthApiMal {

    private final PacientesApi pacientes;

    public AuthApiMal(PacientesApi pacientes) {
        this.pacientes = pacientes;
    }

    public void ejecutar() {
        pacientes.consultar();
    }
}
