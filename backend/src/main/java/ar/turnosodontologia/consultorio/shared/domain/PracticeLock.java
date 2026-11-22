package ar.turnosodontologia.consultorio.shared.domain;

import java.util.UUID;

/**
 * Puerto de serializacion por consultorio (design D3): toma el lock pesimista de la fila del consultorio hasta el
 * fin de la transaccion en curso. Orden de locks unico (siempre {@code practice} primero) para evitar deadlocks.
 */
public interface PracticeLock {

    /** Requiere una transaccion activa; el lock se libera al hacer commit o rollback. */
    void lock(UUID practiceId);
}
