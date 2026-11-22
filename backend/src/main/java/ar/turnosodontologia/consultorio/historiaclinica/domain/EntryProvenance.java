package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Autor (usuario de la sesion) e instante (reloj del servidor) de una entrada clinica. */
public record EntryProvenance(UUID authorId, Instant createdAt) {

    public EntryProvenance {
        Objects.requireNonNull(authorId, "authorId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
