package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OdontogramStateTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-05-01T12:00:00Z");

    private static ToothPosition whole(String tooth) {
        return new ToothPosition(ToothCode.of(tooth), null);
    }

    private static ToothPosition face(String tooth, ToothSurface surface) {
        return new ToothPosition(ToothCode.of(tooth), surface);
    }

    private static OdontogramEntry entry(
            ToothPosition position, ToothCondition condition, int minutes, UUID correctsEntryId) {
        EntryProvenance provenance = new EntryProvenance(AUTHOR_ID, T0.plusSeconds(minutes * 60L));
        OdontogramEntry entry = correctsEntryId == null
                ? OdontogramEntry.register(RECORD_ID, position, condition, null, provenance)
                : OdontogramEntry.correct(
                        RECORD_ID,
                        OdontogramEntry.register(RECORD_ID, position, condition, null, provenance),
                        condition, null, new Correction(correctsEntryId, CorrectionReason.of("motivo")), provenance);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        return entry;
    }

    @Test
    void sinEntradasElEstadoEstaVacio() {
        OdontogramState state = OdontogramState.derive(List.of());

        assertThat(state.current()).isEmpty();
        assertThat(state.history()).isEmpty();
    }

    @Test
    void unaEntradaNuevaReemplazaALaAnteriorDeLaMismaPosicion() {
        OdontogramEntry caries = entry(face("36", ToothSurface.OCCLUSAL), ToothCondition.CARIES, 0, null);
        OdontogramEntry filled = entry(face("36", ToothSurface.OCCLUSAL), ToothCondition.FILLED, 5, null);

        OdontogramState state = OdontogramState.derive(List.of(filled, caries));

        assertThat(state.current()).containsExactly(filled);
        assertThat(state.history()).extracting(OdontogramState.Item::entry).containsExactly(filled, caries);
    }

    @Test
    void laPiezaCompletaYCadaCaraSonPosicionesIndependientes() {
        OdontogramEntry occlusal = entry(face("36", ToothSurface.OCCLUSAL), ToothCondition.CARIES, 0, null);
        OdontogramEntry mesial = entry(face("36", ToothSurface.MESIAL), ToothCondition.CARIES, 1, null);
        OdontogramEntry missing = entry(whole("36"), ToothCondition.MISSING, 2, null);

        OdontogramState state = OdontogramState.derive(List.of(missing, mesial, occlusal));

        assertThat(state.current()).containsExactlyInAnyOrder(occlusal, mesial, missing);
    }

    @Test
    void laPiezaSinRegistrosNoApareceNiSeAsumeUnEstado() {
        OdontogramEntry crown = entry(whole("21"), ToothCondition.CROWN, 0, null);

        OdontogramState state = OdontogramState.derive(List.of(crown));

        assertThat(state.current()).extracting(e -> e.position().tooth().value()).containsExactly("21");
    }

    @Test
    void laEntradaCorregidaSeExcluyeYSuCorreccionEsLaVigente() {
        OdontogramEntry original = entry(whole("46"), ToothCondition.CARIES, 0, null);
        OdontogramEntry correction = entry(whole("46"), ToothCondition.HEALTHY, 10, original.id());

        OdontogramState state = OdontogramState.derive(List.of(correction, original));

        assertThat(state.current()).containsExactly(correction);
        assertThat(state.history()).hasSize(2);
        assertThat(state.history().get(0).entry()).isSameAs(correction);
        assertThat(state.history().get(0).correctedByEntryId()).isNull();
        assertThat(state.history().get(1).entry()).isSameAs(original);
        assertThat(state.history().get(1).correctedByEntryId()).isEqualTo(correction.id());
    }

    @Test
    void siSeCorrigeUnaAltaYaSuperadaLaVigenteSigueSiendoLaMasNueva() {
        OdontogramEntry first = entry(whole("11"), ToothCondition.CARIES, 0, null);
        OdontogramEntry second = entry(whole("11"), ToothCondition.CROWN, 5, null);
        OdontogramEntry correctionOfFirst = entry(whole("11"), ToothCondition.HEALTHY, 3, first.id());

        OdontogramState state = OdontogramState.derive(List.of(second, correctionOfFirst, first));

        assertThat(state.current()).containsExactly(second);
    }

    @Test
    void conIgualInstanteDesempataElIdMayor() {
        OdontogramEntry a = entry(whole("11"), ToothCondition.CARIES, 0, null);
        OdontogramEntry b = entry(whole("11"), ToothCondition.CROWN, 0, null);
        UUID low = UUID.fromString("01900000-0000-7000-8000-000000000001");
        UUID high = UUID.fromString("01900000-0000-7000-8000-000000000002");
        ReflectionTestUtils.setField(a, "id", low);
        ReflectionTestUtils.setField(b, "id", high);

        assertThat(OdontogramState.derive(List.of(a, b)).current()).containsExactly(b);
        assertThat(OdontogramState.derive(List.of(b, a)).current()).containsExactly(b);
        assertThat(OdontogramState.derive(List.of(a, b)).history()).extracting(OdontogramState.Item::entry)
                .containsExactly(b, a);
    }

    @Test
    void elEstadoActualSeOrdenaPorPiezaYCaraConLaPiezaCompletaPrimero() {
        OdontogramEntry t48 = entry(whole("48"), ToothCondition.MISSING, 0, null);
        OdontogramEntry t11Mesial = entry(face("11", ToothSurface.MESIAL), ToothCondition.CARIES, 1, null);
        OdontogramEntry t11Occlusal = entry(face("11", ToothSurface.OCCLUSAL), ToothCondition.FILLED, 2, null);
        OdontogramEntry t11Whole = entry(whole("11"), ToothCondition.CROWN, 3, null);
        OdontogramEntry t51 = entry(whole("51"), ToothCondition.HEALTHY, 4, null);

        OdontogramState state = OdontogramState.derive(List.of(t51, t11Whole, t11Occlusal, t11Mesial, t48));

        assertThat(state.current()).containsExactly(t11Whole, t11Occlusal, t11Mesial, t48, t51);
    }
}
