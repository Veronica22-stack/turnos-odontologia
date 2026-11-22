package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OdontogramEntryTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final ToothPosition OCCLUSAL_36 = new ToothPosition(ToothCode.of("36"), ToothSurface.OCCLUSAL);
    private static final ToothPosition WHOLE_46 = new ToothPosition(ToothCode.of("46"), null);

    private final EntryProvenance provenance = new EntryProvenance(AUTHOR_ID, NOW);

    @Test
    void altaNuevaNoReferenciaNingunaCorreccion() {
        OdontogramEntry entry = OdontogramEntry.register(
                RECORD_ID, OCCLUSAL_36, ToothCondition.CARIES, "mancha", provenance);

        assertThat(entry.clinicalRecordId()).isEqualTo(RECORD_ID);
        assertThat(entry.position()).isEqualTo(OCCLUSAL_36);
        assertThat(entry.condition()).isEqualTo(ToothCondition.CARIES);
        assertThat(entry.notes()).isEqualTo("mancha");
        assertThat(entry.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.isCorrection()).isFalse();
        assertThat(entry.correctionReason()).isNull();
    }

    @Test
    void piezaCompletaNoLlevaCaraNiNotas() {
        OdontogramEntry entry = OdontogramEntry.register(RECORD_ID, WHOLE_46, ToothCondition.MISSING, null, provenance);

        assertThat(entry.position().surface()).isNull();
        assertThat(entry.notes()).isNull();
    }

    @Test
    void laCorreccionCopiaPiezaYCaraDeLaOriginalYLlevaIdYMotivo() {
        OdontogramEntry original = OdontogramEntry.register(
                RECORD_ID, OCCLUSAL_36, ToothCondition.CARIES, null, provenance);
        UUID originalId = UUID.randomUUID();
        Correction correction = new Correction(originalId, CorrectionReason.of("era una obturacion"));

        OdontogramEntry corrected = OdontogramEntry.correct(
                RECORD_ID, original, ToothCondition.FILLED, "resina", correction, provenance);

        assertThat(corrected.position()).isEqualTo(OCCLUSAL_36);
        assertThat(corrected.condition()).isEqualTo(ToothCondition.FILLED);
        assertThat(corrected.correctsEntryId()).isEqualTo(originalId);
        assertThat(corrected.correctionReason()).isEqualTo("era una obturacion");
        assertThat(corrected.isCorrection()).isTrue();
    }

    @Test
    void laCorreccionDePiezaCompletaConservaLaPosicionSinCara() {
        OdontogramEntry original = OdontogramEntry.register(RECORD_ID, WHOLE_46, ToothCondition.CARIES, null, provenance);

        OdontogramEntry corrected = OdontogramEntry.correct(
                RECORD_ID, original, ToothCondition.HEALTHY, null,
                new Correction(UUID.randomUUID(), CorrectionReason.of("error de carga")), provenance);

        assertThat(corrected.position()).isEqualTo(WHOLE_46);
    }

    @Test
    void rechazaCaraConEstadoDePiezaCompleta() {
        assertThatThrownBy(() -> OdontogramEntry.register(
                RECORD_ID, OCCLUSAL_36, ToothCondition.MISSING, null, provenance))
                .isInstanceOf(IllegalArgumentException.class);
        OdontogramEntry original = OdontogramEntry.register(
                RECORD_ID, OCCLUSAL_36, ToothCondition.CARIES, null, provenance);
        assertThatThrownBy(() -> OdontogramEntry.correct(
                RECORD_ID, original, ToothCondition.CROWN, null,
                new Correction(UUID.randomUUID(), CorrectionReason.of("motivo")), provenance))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaNotasDeMasDe500Caracteres() {
        assertThatThrownBy(() -> OdontogramEntry.register(
                RECORD_ID, WHOLE_46, ToothCondition.CARIES, "x".repeat(501), provenance))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(OdontogramEntry.register(
                RECORD_ID, WHOLE_46, ToothCondition.CARIES, "x".repeat(500), provenance).notes()).hasSize(500);
    }

    @Test
    void exigeHistoriaClinicaPosicionYEstado() {
        assertThatThrownBy(() -> OdontogramEntry.register(null, WHOLE_46, ToothCondition.CARIES, null, provenance))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OdontogramEntry.register(RECORD_ID, null, ToothCondition.CARIES, null, provenance))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OdontogramEntry.register(RECORD_ID, WHOLE_46, null, null, provenance))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringNoExponeNotasMotivoPiezaNiEstado() {
        OdontogramEntry original = OdontogramEntry.register(
                RECORD_ID, OCCLUSAL_36, ToothCondition.CARIES, "NOTA-SECRETA", provenance);
        OdontogramEntry corrected = OdontogramEntry.correct(
                RECORD_ID, original, ToothCondition.FILLED, "NOTA-SECRETA",
                new Correction(UUID.randomUUID(), CorrectionReason.of("MOTIVO-SECRETO")), provenance);

        assertThat(corrected.toString()).doesNotContain("NOTA-SECRETA", "MOTIVO-SECRETO", "36", "FILLED", "OCCLUSAL");
        assertThat(corrected.toString()).contains(RECORD_ID.toString());
    }

    @Test
    void odontogramaEsUnTipoDeEntradaConSusNombres() {
        assertThat(ClinicalEntryType.ODONTOGRAM.pathSegment()).isEqualTo("odontogram");
        assertThat(ClinicalEntryType.ODONTOGRAM.auditEntityType()).isEqualTo("odontogram_entry");
    }
}
