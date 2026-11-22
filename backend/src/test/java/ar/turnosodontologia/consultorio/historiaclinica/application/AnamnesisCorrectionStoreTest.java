package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.historiaclinica.application.ClinicalFakes.InMemoryAnamnesis;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisContent;
import ar.turnosodontologia.consultorio.historiaclinica.domain.AnamnesisEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Solo la version vigente (la de mayor numero) de la anamnesis puede corregirse (Q5). */
class AnamnesisCorrectionStoreTest {

    private final UUID recordId = UUID.randomUUID();
    private final InMemoryAnamnesis repository = new InMemoryAnamnesis();
    private final AnamnesisCorrectionStore store = new AnamnesisCorrectionStore(repository);

    @Test
    void laVersionVigenteEsCorregible() {
        seed(1);
        AnamnesisEntry current = seed(2);

        assertThatCode(() -> store.assertCorrectable(current)).doesNotThrowAnyException();
    }

    @Test
    void unaVersionSuperadaNoEsCorregible() {
        AnamnesisEntry superseded = seed(1);
        seed(2);

        assertThatThrownBy(() -> store.assertCorrectable(superseded))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CLINICAL_ENTRY_NOT_CURRENT");
    }

    @Test
    void laVigenteDeOtraHistoriaNoAfectaAEstaHistoria() {
        AnamnesisEntry mine = seed(1);
        repository.save(AnamnesisEntry.register(UUID.randomUUID(), 9,
                AnamnesisContent.of("x", null, null, null, null), new EntryProvenance(UUID.randomUUID(), Instant.now())));

        assertThatCode(() -> store.assertCorrectable(mine)).doesNotThrowAnyException();
    }

    private AnamnesisEntry seed(int version) {
        return repository.save(AnamnesisEntry.register(recordId, version,
                AnamnesisContent.of("x", null, null, null, null), new EntryProvenance(UUID.randomUUID(), Instant.now())));
    }
}
