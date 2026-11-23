package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EvolutionDescriptionTest {

    @Test
    void aceptaUnaDescripcionValidaYConservaElTextoSinRecortar() {
        EvolutionDescription description = EvolutionDescription.of("  Control de rutina.\n");

        assertThat(description.value()).isEqualTo("  Control de rutina.\n");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\n\t "})
    void rechazaDescripcionNulaVaciaOSoloEspacios(String raw) {
        assertThatThrownBy(() -> EvolutionDescription.of(raw))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("description");
                    assertThat(error.constraint()).isEqualTo("NotBlank");
                });
    }

    @Test
    void aceptaExactamente5000CaracteresYRechazaUnoMas() {
        assertThat(EvolutionDescription.of("x".repeat(5000)).value()).hasSize(5000);

        assertThatThrownBy(() -> EvolutionDescription.of("x".repeat(5001)))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("description");
                    assertThat(error.constraint()).isEqualTo("Size");
                });
    }

    @Test
    void toStringNoRevelaElContenidoClinico() {
        assertThat(EvolutionDescription.of("dolor en pieza 16 secreto").toString())
                .doesNotContain("dolor")
                .doesNotContain("secreto");
    }
}
