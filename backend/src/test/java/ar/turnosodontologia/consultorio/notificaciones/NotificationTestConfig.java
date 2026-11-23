package ar.turnosodontologia.consultorio.notificaciones;

import ar.turnosodontologia.consultorio.notificaciones.domain.EmailSender;
import ar.turnosodontologia.consultorio.notificaciones.domain.OutgoingEmail;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Emisor falso controlable y reloj mutable para los ITs de notificaciones (reemplazan a los reales). */
@TestConfiguration
public class NotificationTestConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }

    @Bean
    @Primary
    public ControllableEmailSender controllableEmailSender() {
        return new ControllableEmailSender();
    }

    /** Reloj que sigue al del sistema hasta que un test lo fija con {@link #set(Instant)}. */
    public static final class MutableClock extends Clock {

        private volatile Instant fixed;

        public void set(Instant instant) {
            this.fixed = instant;
        }

        public void reset() {
            this.fixed = null;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            Instant current = fixed;
            return current != null ? current : Instant.now();
        }
    }

    /** Registra los emails "enviados" y falla o demora a pedido. */
    public static final class ControllableEmailSender implements EmailSender {

        private final List<OutgoingEmail> sent = new CopyOnWriteArrayList<>();
        private volatile RuntimeException failure;
        private volatile Consumer<OutgoingEmail> beforeSend;

        @Override
        public void send(OutgoingEmail email) {
            Consumer<OutgoingEmail> hook = beforeSend;
            if (hook != null) {
                hook.accept(email);
            }
            RuntimeException error = failure;
            if (error != null) {
                throw error;
            }
            sent.add(email);
        }

        public List<OutgoingEmail> sent() {
            return List.copyOf(sent);
        }

        public void failWith(RuntimeException error) {
            this.failure = error;
        }

        public void beforeSend(Consumer<OutgoingEmail> hook) {
            this.beforeSend = hook;
        }

        public void reset() {
            sent.clear();
            failure = null;
            beforeSend = null;
        }
    }
}
