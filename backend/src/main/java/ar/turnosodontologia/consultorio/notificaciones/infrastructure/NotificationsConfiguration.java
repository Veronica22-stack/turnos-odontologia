package ar.turnosodontologia.consultorio.notificaciones.infrastructure;

import ar.turnosodontologia.consultorio.notificaciones.application.DispatchTrigger;
import ar.turnosodontologia.consultorio.notificaciones.application.NotificationDispatcher;
import ar.turnosodontologia.consultorio.notificaciones.domain.NotificationRetryPolicy;
import java.util.concurrent.ThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Despacho periodico y disparo inmediato (design D14). Con {@code NOTIFICATION_DISPATCH_ENABLED=false} no hay ni
 * poll ni disparo: las notificaciones se encolan y esperan (rollback operativo y base de los tests).
 */
@Configuration(proxyBeanMethods = false)
class NotificationsConfiguration {

    @Bean
    NotificationRetryPolicy notificationRetryPolicy(
            @Value("${NOTIFICATION_MAX_ATTEMPTS:5}") int maxAttempts) {
        return NotificationRetryPolicy.standard(maxAttempts);
    }

    /** Despacho activo (por defecto): poll periodico y ejecutor asincrono para el disparo inmediato. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "NOTIFICATION_DISPATCH_ENABLED", havingValue = "true", matchIfMissing = true)
    static class DispatchEnabled {

        /** Un hilo y cola de 1: si ya hay un despacho corriendo y otro en cola, el pedido sobra (recorrera lo vencido). */
        @Bean(destroyMethod = "shutdown")
        ThreadPoolTaskExecutor notificationDispatchExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setThreadNamePrefix("notif-dispatch-");
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(1);
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds(15);
            return executor;
        }

        @Bean
        DispatchTrigger asyncDispatchTrigger(ThreadPoolTaskExecutor notificationDispatchExecutor,
                NotificationDispatchJob job) {
            return () -> notificationDispatchExecutor.execute(job::run);
        }

        @Bean
        NotificationDispatchJob notificationDispatchJob(NotificationDispatcher dispatcher) {
            return new NotificationDispatchJob(dispatcher);
        }
    }

    /** Despacho desactivado: el encolado funciona pero nada se envia ni se pide enviar. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "NOTIFICATION_DISPATCH_ENABLED", havingValue = "false")
    static class DispatchDisabled {

        @Bean
        DispatchTrigger noOpDispatchTrigger() {
            return () -> {
            };
        }
    }

    /** Tarea periodica; tambien la usa el disparo inmediato. Un error nunca debe matar al planificador. */
    static class NotificationDispatchJob {

        private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatchJob.class);

        private final NotificationDispatcher dispatcher;

        NotificationDispatchJob(NotificationDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @Scheduled(fixedDelayString = "${NOTIFICATION_POLL_INTERVAL:PT5M}", initialDelayString = "PT30S")
        void run() {
            try {
                dispatcher.dispatchDue();
            } catch (RuntimeException error) {
                LOG.warn("Error en el despacho de notificaciones error={}", error.getClass().getSimpleName());
            }
        }
    }
}
