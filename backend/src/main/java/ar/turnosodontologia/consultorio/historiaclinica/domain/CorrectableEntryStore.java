package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Almacen de un tipo de entrada clinica para el mecanismo generico de correcciones. Cada tipo (anamnesis, y luego
 * odontograma y evoluciones) lo implementa y puede restringir que entradas son corregibles.
 */
public interface CorrectableEntryStore<E extends ClinicalEntry> {

    /** Busca la entrada solo dentro de la historia clinica indicada. */
    Optional<E> findInRecord(UUID clinicalRecordId, UUID entryId);

    boolean isCorrected(UUID entryId);

    /** Persiste la entrada y fuerza el volcado a la base (para que las restricciones se evaluen en la operacion). */
    E save(E entry);

    /** Gancho de cada tipo para rechazar la correccion de entradas no corregibles; por defecto todas lo son. */
    default void assertCorrectable(E original) {
    }
}
