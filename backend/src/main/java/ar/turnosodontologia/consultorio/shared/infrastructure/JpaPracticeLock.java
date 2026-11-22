package ar.turnosodontologia.consultorio.shared.infrastructure;

import ar.turnosodontologia.consultorio.shared.domain.PracticeLock;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** {@link PracticeLock} con {@code SELECT ... FOR UPDATE} sobre la fila de {@code practice}. */
@Component
class JpaPracticeLock implements PracticeLock {

    private final EntityManager entityManager;

    JpaPracticeLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID practiceId) {
        entityManager.createNativeQuery("SELECT id FROM practice WHERE id = :id FOR UPDATE")
                .setParameter("id", practiceId)
                .getResultList();
    }
}
