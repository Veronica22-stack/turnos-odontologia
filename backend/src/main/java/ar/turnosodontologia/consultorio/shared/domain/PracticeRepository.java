package ar.turnosodontologia.consultorio.shared.domain;

import java.util.List;

/** Puerto de lectura del consultorio. */
public interface PracticeRepository {

    List<Practice> findAll();
}
