package ar.turnosodontologia.consultorio.auth.infrastructure;

import ar.turnosodontologia.consultorio.auth.application.BootstrapDentist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Dispara el bootstrap del DENTIST al arrancar (despues de Flyway). Lee BOOTSTRAP_ADMIN_EMAIL y
 * BOOTSTRAP_ADMIN_PASSWORD del entorno sin mapearlas en {@code application.yml} (no hay valores por defecto versionados
 * para un secreto). Una excepcion aborta el arranque. Solo se loguea el id de la cuenta (regla dura 6).
 */
@Component
class BootstrapDentistRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapDentistRunner.class);

    private final BootstrapDentist bootstrapDentist;
    private final Environment environment;

    BootstrapDentistRunner(BootstrapDentist bootstrapDentist, Environment environment) {
        this.bootstrapDentist = bootstrapDentist;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapDentist.Result result = bootstrapDentist.execute(environment::getProperty);
        if (result.outcome() == BootstrapDentist.Outcome.CREATED) {
            LOG.info("Bootstrap: cuenta DENTIST creada (id={})", result.userId());
        } else {
            LOG.info("Bootstrap omitido: ya existe una cuenta DENTIST");
        }
    }
}
