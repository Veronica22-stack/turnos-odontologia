package ar.turnosodontologia.consultorio.support;

import ar.turnosodontologia.consultorio.ConsultorioApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;

/**
 * Base de los tests de integración: aplicación completa contra el PostgreSQL embebido compartido de la JVM
 * (datasource con el rol {@code app}, Flyway con el rol {@code migrator}). Las credenciales de bootstrap del DENTIST
 * (C-02) llegan con {@link EmbeddedPostgresSupport.Database#properties()}.
 */
@SpringBootTest(classes = ConsultorioApplication.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
public abstract class AbstractIntegrationTest {

    protected static final EmbeddedPostgresSupport.Database DATABASE = EmbeddedPostgresSupport.shared();

    /** Email del DENTIST creado en el arranque (ya normalizado). */
    protected static final String BOOTSTRAP_EMAIL = EmbeddedPostgresSupport.BOOTSTRAP_EMAIL;

    protected static final String BOOTSTRAP_PASSWORD = EmbeddedPostgresSupport.BOOTSTRAP_PASSWORD;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        DATABASE.properties().forEach((name, value) -> registry.add(name, () -> value));
    }
}
