package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvolutionEntryTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");

    private final EntryProvenance provenance = new EntryProvenance(AUTHOR_ID, NOW);

    @Test
    void elTipoDeEntradaUsaElSegmentoEnPluralYLaTablaPropia() {
        assertThat(ClinicalEntryType.EVOLUTION.pathSegment()).isEqualTo("evolutions");
        assertThat(ClinicalEntryType.EVOLUTION.auditEntityType()).isEqualTo("evolution_entry");
    }

    @Test
    void altaSinTurnoGuardaDescripcionAutorEInstante() {
        EvolutionEntry entry = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("Control de rutina"), null, provenance);

        assertThat(entry.clinicalRecordId()).isEqualTo(RECORD_ID);
        assertThat(entry.description()).isEqualTo("Control de rutina");
        assertThat(entry.appointmentId()).isNull();
        assertThat(entry.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.isCorrection()).isFalse();
        assertThat(entry.correctionReason()).isNull();
    }

    @Test
    void altaConTurnoGuardaElIdentificadorDelTurno() {
        UUID appointmentId = UUID.randomUUID();

        EvolutionEntry entry = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("Obturacion"), appointmentId, provenance);

        assertThat(entry.appointmentId()).isEqualTo(appointmentId);
    }

    @Test
    void laCorreccionCopiaElTurnoDeLaOriginalYLlevaIdYMotivo() {
        UUID appointmentId = UUID.randomUUID();
        EvolutionEntry original = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("texto erroneo"), appointmentId, provenance);
        UUID originalId = UUID.randomUUID();
        Correction correction = new Correction(originalId, CorrectionReason.of("error de tipeo"));

        EvolutionEntry corrected = EvolutionEntry.correct(
                RECORD_ID, original, EvolutionDescription.of("texto correcto"), correction, provenance);

        assertThat(corrected.description()).isEqualTo("texto correcto");
        assertThat(corrected.appointmentId()).isEqualTo(appointmentId);
        assertThat(corrected.correctsEntryId()).isEqualTo(originalId);
        assertThat(corrected.correctionReason()).isEqualTo("error de tipeo");
        assertThat(corrected.isCorrection()).isTrue();
    }

    @Test
    void laCorreccionDeUnaEvolucionSinTurnoTampocoTieneTurno() {
        EvolutionEntry original = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("sin turno"), null, provenance);

        EvolutionEntry corrected = EvolutionEntry.correct(
                RECORD_ID, original, EvolutionDescription.of("corregida"),
                new Correction(UUID.randomUUID(), CorrectionReason.of("motivo")), provenance);

        assertThat(corrected.appointmentId()).isNull();
    }

    @Test
    void toStringNoRevelaDescripcionNiMotivo() {
        EvolutionEntry original = EvolutionEntry.register(
                RECORD_ID, EvolutionDescription.of("descripcion-secreta"), null, provenance);
        EvolutionEntry corrected = EvolutionEntry.correct(
                RECORD_ID, original, EvolutionDescription.of("nueva-secreta"),
                new Correction(UUID.randomUUID(), CorrectionReason.of("motivo-secreto")), provenance);

        assertThat(original.toString()).doesNotContain("secreta");
        assertThat(corrected.toString()).doesNotContain("secreta").doesNotContain("secreto");
    }
}
