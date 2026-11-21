package ar.turnosodontologia.consultorio.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL real y temporal para los tests de integración (zonky, design.md D7).
 *
 * <p>No se usa {@code @AutoConfigureEmbeddedDatabase} ni {@code @ServiceConnection} a propósito: reemplazarían el
 * {@code DataSource} por uno de superusuario y se perdería la verificación de que la aplicación corre sin DDL.
 * Aquí se aplica el MISMO script de roles que se ejecuta a mano ({@code 01-roles.sql}) y la aplicación se conecta con
 * el rol {@code app} y Flyway con el rol {@code migrator}.
 */
public final class EmbeddedPostgresSupport {

    private static final Path ROLES_SCRIPT = Path.of("..", "infra", "postgres", "init", "01-roles.sql");
    private static final String SUPERUSER = "postgres";
    private static final String DB_NAME = "consultorio";

    private static Database shared;

    private EmbeddedPostgresSupport() {
    }

    /** Instancia de PostgreSQL embebido (UTF8, locale C: no depende del idioma del sistema operativo). */
    public static Instance startInstance() throws IOException {
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setLocaleConfig("encoding", "UTF8")
                .setLocaleConfig("locale", "C")
                .setDataDirectoryCustomizer(EmbeddedPostgresSupport::requirePasswordsExceptForSuperuser)
                .start();
        return new Instance(postgres);
    }

    /**
     * zonky arranca con autenticación {@code trust} para todos. Se la reemplaza para que solo el superusuario entre
     * sin contraseña: así los roles de la aplicación se autentican de verdad (como en un PostgreSQL instalado) y un
     * test puede comprobar que una contraseña incorrecta es rechazada.
     */
    private static void requirePasswordsExceptForSuperuser(File dataDirectory) {
        String hba = """
                host all postgres 127.0.0.1/32 trust
                host all postgres ::1/128 trust
                host all all 127.0.0.1/32 scram-sha-256
                host all all ::1/128 scram-sha-256
                """;
        try {
            Files.writeString(dataDirectory.toPath().resolve("pg_hba.conf"), hba);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    /** Instancia compartida por toda la JVM, ya con los roles y la base creados (sin {@code @DirtiesContext}). */
    public static synchronized Database shared() {
        if (shared == null) {
            shared = startDatabase();
        }
        return shared;
    }

    /** Instancia propia, ya con los roles y la base creados; la cierra quien la pide. */
    public static Database startDatabase() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Credentials credentials =
                new Credentials(DB_NAME, "migrator", "mig-" + suffix, "app", "app-" + suffix);
        try {
            Instance instance = startInstance();
            RolesScript.execute(
                    Files.readString(ROLES_SCRIPT), credentials.variables(), "postgres", instance::superuserConnection);
            return new Database(instance, credentials);
        } catch (IOException | SQLException error) {
            throw new IllegalStateException("No se pudo preparar el PostgreSQL embebido: " + error.getMessage(), error);
        }
    }

    /** Credenciales generadas en cada ejecución (nunca versionadas). */
    public record Credentials(String dbName, String migratorUser, String migratorPassword, String appUser, String appPassword) {

        /** Variables de psql que espera {@code 01-roles.sql}. */
        public Map<String, String> variables() {
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("db_name", dbName);
            variables.put("migrator_user", migratorUser);
            variables.put("migrator_password", migratorPassword);
            variables.put("app_user", appUser);
            variables.put("app_password", appPassword);
            return variables;
        }
    }

    /** Instancia embebida en ejecución. */
    public static final class Instance implements Closeable {

        private final EmbeddedPostgres postgres;

        private Instance(EmbeddedPostgres postgres) {
            this.postgres = postgres;
        }

        public String jdbcUrl(String database) {
            return "jdbc:postgresql://localhost:" + postgres.getPort() + "/" + database;
        }

        /** Conexión como superusuario (autenticación local de confianza del PostgreSQL embebido). */
        public Connection superuserConnection(String database) throws SQLException {
            return DriverManager.getConnection(jdbcUrl(database), SUPERUSER, "");
        }

        @Override
        public void close() throws IOException {
            postgres.close();
        }
    }

    /** Instancia con la base y los roles de la aplicación ya creados. */
    public record Database(Instance instance, Credentials credentials) implements Closeable {

        public String jdbcUrl() {
            return instance.jdbcUrl(credentials.dbName());
        }

        /** Propiedades con los nombres de las variables de entorno que consume application.yml. */
        public Map<String, String> properties() {
            Map<String, String> properties = new LinkedHashMap<>();
            properties.put("DB_URL", jdbcUrl());
            properties.put("DB_USER", credentials.appUser());
            properties.put("DB_PASSWORD", credentials.appPassword());
            properties.put("DB_MIGRATION_USER", credentials.migratorUser());
            properties.put("DB_MIGRATION_PASSWORD", credentials.migratorPassword());
            return properties;
        }

        public Connection migratorConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl(), credentials.migratorUser(), credentials.migratorPassword());
        }

        public Connection appConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl(), credentials.appUser(), credentials.appPassword());
        }

        @Override
        public void close() {
            try {
                instance.close();
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }
    }
}
