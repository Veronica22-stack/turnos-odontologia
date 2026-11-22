package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.List;
import java.util.UUID;

/** Puerto de persistencia del odontograma; sin operaciones de borrado ni de edicion. */
public interface OdontogramEntryRepository extends CorrectableEntryStore<OdontogramEntry> {

    /** Todas las entradas de la historia clinica, de la mas nueva a la mas antigua ({@code created_at DESC, id DESC}). */
    List<OdontogramEntry> findNewestFirst(UUID clinicalRecordId);

    /** Si existe en la misma posicion (pieza y cara, o pieza completa) una entrada posterior a la de referencia. */
    boolean existsNewerAtPosition(UUID clinicalRecordId, OdontogramEntry reference);
}
