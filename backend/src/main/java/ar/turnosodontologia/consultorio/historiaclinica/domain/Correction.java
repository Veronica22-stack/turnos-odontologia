package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.Objects;
import java.util.UUID;

/** Referencia de una entrada a la entrada original que corrige, con su motivo. */
public record Correction(UUID originalEntryId, CorrectionReason reason) {

    public Correction {
        Objects.requireNonNull(originalEntryId, "originalEntryId");
        Objects.requireNonNull(reason, "reason");
    }
}
