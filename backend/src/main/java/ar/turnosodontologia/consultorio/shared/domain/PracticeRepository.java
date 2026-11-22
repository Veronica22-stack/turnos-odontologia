package ar.turnosodontologia.consultorio.shared.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de acceso y persistencia del consultorio. */
@FunctionalInterface
public interface PracticeRepository {

    List<Practice> findAll();

    default Optional<Practice> findById(UUID id) {
        return findAll().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    default <S extends Practice> S save(S practice) {
        throw new UnsupportedOperationException("save no implementado en este adaptador");
    }
}
