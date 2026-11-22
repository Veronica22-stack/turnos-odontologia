package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

class CorrectionReasonTest {

    @Test
    void rechazaMotivoNuloVacioOSoloEspacios() {
        for (String invalid : new String[] {null, "", "   ", "\t\n"}) {
            assertThatThrownBy(() -> CorrectionReason.of(invalid))
                    .isInstanceOf(ValidationException.class)
                    .satisfies(error -> assertThat(((ValidationException) error).field()).isEqualTo("reason"));
        }
    }

    @Test
    void rechazaMotivoDeMasDe500Caracteres() {
        assertThatThrownBy(() -> CorrectionReason.of("x".repeat(501)))
                .isInstanceOf(ValidationException.class)
                .satisfies(error -> assertThat(((ValidationException) error).field()).isEqualTo("reason"));
    }

    @Test
    void aceptaExactamente500Caracteres() {
        assertThat(CorrectionReason.of("x".repeat(500)).value()).hasSize(500);
    }

    @Test
    void recortaEspaciosDeLosExtremos() {
        assertThat(CorrectionReason.of("  alergia mal cargada \n").value()).isEqualTo("alergia mal cargada");
    }

    @Test
    void recortaAntesDeValidarElLargo() {
        assertThat(CorrectionReason.of("  " + "x".repeat(500) + "  ").value()).hasSize(500);
    }

    @Test
    void toStringNoContieneElTexto() {
        assertThat(CorrectionReason.of("dato clinico sensible").toString()).doesNotContain("sensible", "clinico");
    }
}
