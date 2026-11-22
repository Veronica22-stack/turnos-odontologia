package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ToothConditionTest {

    @ParameterizedTest
    @EnumSource(value = ToothCondition.class, names = {"CARIES", "FILLED"})
    void cariesYObturacionAdmitenCara(ToothCondition condition) {
        assertThat(condition.allowsSurface()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ToothCondition.class, names = {"HEALTHY", "MISSING", "CROWN", "ROOT_CANAL"})
    void losDemasEstadosSonDePiezaCompleta(ToothCondition condition) {
        assertThat(condition.allowsSurface()).isFalse();
    }

    @Test
    void elCatalogoTieneSeisEstadosYCincoCaras() {
        assertThat(ToothCondition.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("HEALTHY", "CARIES", "FILLED", "MISSING", "CROWN", "ROOT_CANAL");
        assertThat(ToothSurface.values()).extracting(Enum::name)
                .containsExactlyInAnyOrder("OCCLUSAL", "MESIAL", "DISTAL", "VESTIBULAR", "LINGUAL");
    }

    @Test
    void laPosicionDistingueLaPiezaCompletaDeCadaCara() {
        ToothCode tooth = ToothCode.of("36");

        ToothPosition whole = new ToothPosition(tooth, null);
        ToothPosition occlusal = new ToothPosition(tooth, ToothSurface.OCCLUSAL);

        assertThat(whole).isNotEqualTo(occlusal);
        assertThat(occlusal).isEqualTo(new ToothPosition(ToothCode.of("36"), ToothSurface.OCCLUSAL));
        assertThat(occlusal).hasSameHashCodeAs(new ToothPosition(ToothCode.of("36"), ToothSurface.OCCLUSAL));
        assertThat(occlusal).isNotEqualTo(new ToothPosition(tooth, ToothSurface.MESIAL));
        assertThat(whole.hasSurface()).isFalse();
        assertThat(occlusal.hasSurface()).isTrue();
    }
}
