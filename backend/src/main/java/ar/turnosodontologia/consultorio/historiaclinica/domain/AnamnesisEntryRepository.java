package ar.turnosodontologia.consultorio.historiaclinica.domain;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Puerto de persistencia de la anamnesis; sin operaciones de borrado ni de edicion. */
public interface AnamnesisEntryRepository extends CorrectableEntryStore<AnamnesisEntry> {

    /** Todas las versiones de la historia clinica, de la mas nueva a la mas antigua. */
    List<AnamnesisEntry> findNewestFirst(UUID clinicalRecordId);

    /** Para cada entrada corregida de la historia clinica, el id de la entrada que la corrige. */
    Map<UUID, UUID> correctedByIndex(UUID clinicalRecordId);

    /** Mayor numero de version de la historia clinica, o 0 si no tiene anamnesis. */
    int maxVersion(UUID clinicalRecordId);
}
