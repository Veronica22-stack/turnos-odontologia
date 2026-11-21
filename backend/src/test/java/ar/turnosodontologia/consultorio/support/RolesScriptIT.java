package ar.turnosodontologia.consultorio.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Ejecuta el script real infra/postgres/init/01-roles.sql contra un PostgreSQL embebido propio (aislado del singleton). */
class RolesScriptIT {

    private static final Path SCRIPT = Path.of("..", "infra", "postgres", "init", "01-roles.sql");

    private static EmbeddedPostgresSupport.Instance postgres;

    @BeforeAll
    static void startPostgres() throws IOException {
        postgres = EmbeddedPostgresSupport.startInstance();
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        postgres.close();
    }

    private record Names(String db, String migrator, String migratorPassword, String app, String appPassword) {

        static Names unique() {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            return new Names("db_" + suffix, "mig_" + suffix, "mig-pw-" + suffix, "app_" + suffix, "app-pw-" + suffix);
        }

        Map<String, String> variables() {
            Map<String, String> variables = new HashMap<>();
            variables.put("db_name", db);
            variables.put("migrator_user", migrator);
            variables.put("migrator_password", migratorPassword);
            variables.put("app_user", app);
            variables.put("app_password", appPassword);
            return variables;
        }

        Names withNewPasswords() {
            return new Names(db, migrator, migratorPassword + "-2", app, appPassword + "-2");
        }
    }

    private static void runRolesScript(Names names) throws IOException, SQLException {
        RolesScript.execute(Files.readString(SCRIPT), names.variables(), "postgres", postgres::superuserConnection);
    }

    private static Connection connect(String database, String user, String password) throws SQLException {
        return DriverManager.getConnection(postgres.jdbcUrl(database), user, password);
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection connection = postgres.superuserConnection("postgres");
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    @Test
    void creaRolesSinPrivilegiosYUnaBaseDeLaQueEsDuenoElMigrador() throws Exception {
        Names names = Names.unique();

        runRolesScript(names);

        for (String role : new String[] {names.migrator(), names.app()}) {
            assertThat(scalar("SELECT rolcanlogin::text || rolsuper::text || rolcreatedb::text || rolcreaterole::text "
                    + "FROM pg_roles WHERE rolname = '" + role + "'"))
                    .as("atributos de " + role)
                    .isEqualTo("truefalsefalsefalse");
        }
        assertThat(scalar("SELECT r.rolname FROM pg_database d JOIN pg_roles r ON r.oid = d.datdba "
                + "WHERE d.datname = '" + names.db() + "'"))
                .isEqualTo(names.migrator());
    }

    @Test
    void elRolDeAplicacionSeConectaPeroNoPuedeCrearObjetosEnPublic() throws Exception {
        Names names = Names.unique();
        runRolesScript(names);

        try (Connection connection = connect(names.db(), names.app(), names.appPassword());
                Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE no_deberia_existir (id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        try (Connection connection = connect(names.db(), names.migrator(), names.migratorPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE si_puede (id int)");
        }
    }

    @Test
    void laSegundaEjecucionEsIdempotenteConservaDatosYRotaLasContrasenas() throws Exception {
        Names first = Names.unique();
        runRolesScript(first);
        try (Connection connection = connect(first.db(), first.migrator(), first.migratorPassword());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE datos (id int)");
            statement.execute("INSERT INTO datos VALUES (42)");
        }
        Names second = first.withNewPasswords();

        runRolesScript(second);

        assertThat(scalar("SELECT count(*) FROM pg_roles WHERE rolname IN ('" + first.migrator() + "', '" + first.app() + "')"))
                .isEqualTo("2");
        assertThat(scalar("SELECT count(*) FROM pg_database WHERE datname = '" + first.db() + "'")).isEqualTo("1");
        try (Connection connection = connect(second.db(), second.migrator(), second.migratorPassword());
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT id FROM datos")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(42);
        }
        try (Connection connection = connect(second.db(), second.app(), second.appPassword())) {
            assertThat(connection.isValid(2)).isTrue();
        }
        assertThatThrownBy(() -> connect(second.db(), second.app(), first.appPassword()))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("authentication");
    }

    @Test
    void sinLaContrasenaDelRolDeAplicacionFallaAntesDeCrearNada() throws Exception {
        Names names = Names.unique();
        Map<String, String> variables = names.variables();
        variables.remove("app_password");

        assertThatThrownBy(() -> RolesScript.execute(
                        Files.readString(SCRIPT), variables, "postgres", postgres::superuserConnection))
                .hasMessageContaining("app_password");

        assertThat(scalar("SELECT count(*) FROM pg_roles WHERE rolname IN ('" + names.migrator() + "', '" + names.app() + "')"))
                .isEqualTo("0");
        assertThat(scalar("SELECT count(*) FROM pg_database WHERE datname = '" + names.db() + "'")).isEqualTo("0");
    }

    @Test
    void unaSentenciaInvalidaFallaIndicandoElErrorSinExponerContrasenas() {
        Names names = Names.unique();
        String script = "SELECT :'app_password';\nSELECT * FROM tabla_que_no_existe;\n";

        assertThatThrownBy(() -> RolesScript.execute(script, names.variables(), "postgres", postgres::superuserConnection))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("tabla_que_no_existe")
                .hasMessageNotContaining(names.appPassword());
    }
}
