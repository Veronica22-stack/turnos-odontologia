package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.List;
import java.util.UUID;

/** Puerto de persistencia de las evoluciones; sin operaciones de borrado ni de edicion. */
public interface EvolutionEntryRepository extends CorrectableEntryStore<EvolutionEntry> {

    /** Todas las evoluciones de la historia clinica, de la mas nueva a la mas antigua ({@code created_at DESC, id DESC}). */
    List<EvolutionEntry> findNewestFirst(UUID clinicalRecordId);
}
