package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeLock;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

/** El lock por consultorio serializa transacciones (design D3) y exige una transaccion activa. */
class PracticeLockIT extends AbstractIntegrationTest {

    private static final long WAIT_SECONDS = 10;

    private final PracticeLock lock;
    private final TransactionTemplate transactions;
    private final InstallationPractice practice;

    PracticeLockIT(PracticeLock lock, TransactionTemplate transactions, InstallationPractice practice) {
        this.lock = lock;
        this.transactions = transactions;
        this.practice = practice;
    }

    @Test
    void unaSegundaTransaccionEsperaHastaElCommitDeLaPrimera() throws Exception {
        UUID practiceId = practice.id();
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicBoolean firstCommitted = new AtomicBoolean(false);
        AtomicBoolean secondAcquiredBeforeFirstCommit = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                lock.lock(practiceId);
                firstHoldsLock.countDown();
                await(releaseFirst);
                firstCommitted.set(true);
            }));
            assertThat(firstHoldsLock.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactions.executeWithoutResult(status -> {
                lock.lock(practiceId);
                secondAcquiredBeforeFirstCommit.set(!firstCommitted.get());
            }));

            // Mientras la primera retiene el lock, la segunda no puede terminar.
            assertThatThrownBy(() -> second.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            releaseFirst.countDown();
            first.get(WAIT_SECONDS, TimeUnit.SECONDS);
            second.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(secondAcquiredBeforeFirstCommit.get()).isFalse();
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void despuesDelCommitElLockQuedaLibreParaOtraTransaccion() {
        UUID practiceId = practice.id();

        transactions.executeWithoutResult(status -> lock.lock(practiceId));
        transactions.executeWithoutResult(status -> lock.lock(practiceId));
    }

    @Test
    void sinTransaccionActivaFalla() {
        assertThatThrownBy(() -> lock.lock(practice.id())).isInstanceOf(RuntimeException.class);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timeout esperando el latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
