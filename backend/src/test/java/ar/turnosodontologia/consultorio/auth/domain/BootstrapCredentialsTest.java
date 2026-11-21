package ar.turnosodontologia.consultorio.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BootstrapCredentialsTest {

    @Test
    void aceptaEmailValidoYContrasenaDeDoceCaracteres() {
        BootstrapCredentials credentials = BootstrapCredentials.of("dr@ejemplo.com", "123456789012");

        assertThat(credentials.email()).isEqualTo("dr@ejemplo.com");
        assertThat(credentials.password()).isEqualTo("123456789012");
    }

    @Test
    void aceptaOtroEmailYUnaContrasenaMasLarga() {
        BootstrapCredentials credentials = BootstrapCredentials.of("otra.persona@clinica.org.ar", "una-contrasena-bastante-larga");

        assertThat(credentials.email()).isEqualTo("otra.persona@clinica.org.ar");
    }

    @Test
    void rechazaContrasenaCortaNombrandoLaVariableSinMostrarla() {
        assertThatThrownBy(() -> BootstrapCredentials.of("dr@ejemplo.com", "corta123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD")
                .hasMessageNotContaining("corta123");
    }

    @Test
    void rechazaEmailSinFormatoNombrandoLaVariableSinMostrarlo() {
        assertThatThrownBy(() -> BootstrapCredentials.of("no-es-un-email", "123456789012"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
                .hasMessageNotContaining("no-es-un-email");
    }

    @Test
    void rechazaEmailDeMasDe254Caracteres() {
        String longEmail = "a".repeat(250) + "@b.co";

        assertThatThrownBy(() -> BootstrapCredentials.of(longEmail, "123456789012"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_EMAIL")
                .hasMessageNotContaining(longEmail);
    }

    @Test
    void toStringNoContieneEmailNiContrasena() {
        BootstrapCredentials credentials = BootstrapCredentials.of("dr@ejemplo.com", "123456789012");

        assertThat(credentials.toString())
                .isEqualTo("BootstrapCredentials[email=***, password=***]")
                .doesNotContain("dr@ejemplo.com")
                .doesNotContain("123456789012");
    }
}
