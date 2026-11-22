package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnamnesisEntryTest {

    private static final UUID RECORD_ID = UUID.randomUUID();
    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-05-01T12:00:00Z");
    private static final AnamnesisContent CONTENT = AnamnesisContent.of("penicilina", null, null, null, null);

    private final EntryProvenance provenance = new EntryProvenance(AUTHOR_ID, NOW);

    @Test
    void altaNuevaNoReferenciaNingunaCorreccion() {
        AnamnesisEntry entry = AnamnesisEntry.register(RECORD_ID, 1, CONTENT, provenance);

        assertThat(entry.clinicalRecordId()).isEqualTo(RECORD_ID);
        assertThat(entry.version()).isEqualTo(1);
        assertThat(entry.content()).isEqualTo(CONTENT);
        assertThat(entry.authorId()).isEqualTo(AUTHOR_ID);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.correctsEntryId()).isNull();
        assertThat(entry.correctionReason()).isNull();
        assertThat(entry.isCorrection()).isFalse();
    }

    @Test
    void laCorreccionLlevaElIdDeLaOriginalYElMotivo() {
        UUID originalId = UUID.randomUUID();
        Correction correction = new Correction(originalId, CorrectionReason.of("alergia mal cargada"));

        AnamnesisEntry entry = AnamnesisEntry.correct(RECORD_ID, 4, CONTENT, correction, provenance);

        assertThat(entry.correctsEntryId()).isEqualTo(originalId);
        assertThat(entry.correctionReason()).isEqualTo("alergia mal cargada");
        assertThat(entry.version()).isEqualTo(4);
        assertThat(entry.isCorrection()).isTrue();
    }

    @Test
    void exigeAutorEInstanteDelServidor() {
        assertThatThrownBy(() -> new EntryProvenance(null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EntryProvenance(AUTHOR_ID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void laVersionDebeSerAlMenosUno() {
        assertThatThrownBy(() -> AnamnesisEntry.register(RECORD_ID, 0, CONTENT, provenance))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AnamnesisEntry.register(RECORD_ID, -3, CONTENT, provenance))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exigeContenidoYHistoriaClinica() {
        assertThatThrownBy(() -> AnamnesisEntry.register(null, 1, CONTENT, provenance))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AnamnesisEntry.register(RECORD_ID, 1, null, provenance))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void anamnesisEsElUnicoTipoDeEntradaYExponeSusNombres() {
        assertThat(ClinicalEntryType.ANAMNESIS.auditEntityType()).isEqualTo("anamnesis_entry");
        assertThat(ClinicalEntryType.ANAMNESIS.pathSegment()).isEqualTo("anamnesis");
    }
}
