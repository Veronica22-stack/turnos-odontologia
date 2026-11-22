package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ToothCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"11", "18", "41", "48"})
    void aceptaPiezasPermanentes(String code) {
        ToothCode tooth = ToothCode.of(code);

        assertThat(tooth.value()).isEqualTo(code);
        assertThat(tooth.dentition()).isEqualTo(ToothCode.Dentition.PERMANENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"51", "55", "81", "85"})
    void aceptaPiezasTemporales(String code) {
        ToothCode tooth = ToothCode.of(code);

        assertThat(tooth.value()).isEqualTo(code);
        assertThat(tooth.dentition()).isEqualTo(ToothCode.Dentition.TEMPORARY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"10", "19", "49", "56", "86", "90", "00", "1", "111", "ab", "1a", " 11", "11 ", "-1"})
    void rechazaCodigosInvalidosSinEcoDelValor(String code) {
        assertThatThrownBy(() -> ToothCode.of(code))
                .isInstanceOfSatisfying(ValidationException.class, error -> {
                    assertThat(error.field()).isEqualTo("toothCode");
                    if (code != null && !code.isEmpty()) {
                        assertThat(String.valueOf(error.getMessage())).doesNotContain(code);
                    }
                });
    }

    @Test
    void sonIgualesPorValor() {
        assertThat(ToothCode.of("36")).isEqualTo(ToothCode.of("36")).isNotEqualTo(ToothCode.of("37"));
    }
}
