package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

/** Las migraciones son inmutables: reaplicar no hace nada y editar una ya aplicada se detecta por checksum. */
class MigrationValidationIT extends AbstractIntegrationTest {

    private Flyway flywayAt(String location) {
        return Flyway.configure()
                .dataSource(DATABASE.jdbcUrl(), DATABASE.credentials().migratorUser(), DATABASE.credentials().migratorPassword())
                .locations(location)
                .placeholders(java.util.Map.of("app_role", DATABASE.credentials().appUser()))
                .load();
    }

    @Test
    void migrarDosVecesNoAplicaNadaLaSegundaVez() {
        Flyway flyway = flywayAt("classpath:db/migration");

        MigrateResult first = flyway.migrate();
        MigrateResult second = flyway.migrate();

        assertThat(first.success).isTrue();
        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void validarContraUnaV1AlteradaFallaPorChecksum() {
        Flyway flyway = flywayAt("classpath:db/migration_alterada");

        assertThatThrownBy(flyway::validate)
                .isInstanceOf(FlywayValidateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void validarContraLasMigracionesRealesPasa() {
        Flyway flyway = flywayAt("classpath:db/migration");

        flyway.validate();

        assertThat(flyway.info().applied()).isNotEmpty();
    }
}
