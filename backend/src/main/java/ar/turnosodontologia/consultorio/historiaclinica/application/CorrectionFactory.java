package ar.turnosodontologia.consultorio.historiaclinica.application;

import ar.turnosodontologia.consultorio.historiaclinica.domain.ClinicalEntry;
import ar.turnosodontologia.consultorio.historiaclinica.domain.Correction;
import ar.turnosodontologia.consultorio.historiaclinica.domain.EntryProvenance;
import java.util.UUID;

/** Construye la entrada corregida de un tipo concreto a partir de la original, la referencia y el autor/instante. */
@FunctionalInterface
public interface CorrectionFactory<E extends ClinicalEntry> {

    E create(UUID clinicalRecordId, Correction correction, EntryProvenance provenance, E original);
}
