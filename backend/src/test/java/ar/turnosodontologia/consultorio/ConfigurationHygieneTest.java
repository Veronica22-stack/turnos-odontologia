package ar.turnosodontologia.consultorio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Verifica que la plantilla de entorno este completa y que ningun YAML versionado lleve secretos. */
class ConfigurationHygieneTest {

    private static final Path ENV_EXAMPLE = Path.of("..", ".env.example");
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path ROLES_SCRIPT = Path.of("..", "infra", "postgres", "init", "01-roles.sql");
    private static final Pattern PASSWORD_VARIABLE = Pattern.compile(":'\\w*password\\w*'", Pattern.CASE_INSENSITIVE);

    /** Variables de KB 08 (Variables de entorno) mas DB_NAME (usada por el script de roles). */
    private static final List<String> EXPECTED_VARIABLES = List.of(
            "SPRING_PROFILES_ACTIVE",
            "DB_URL", "DB_USER", "DB_PASSWORD",
            "DB_MIGRATION_USER", "DB_MIGRATION_PASSWORD",
            "APP_BASE_URL", "APP_TIMEZONE",
            "BOOTSTRAP_ADMIN_EMAIL", "BOOTSTRAP_ADMIN_PASSWORD",
            "AUTH_SESSION_TIMEOUT_MIN", "AUTH_SESSION_COOKIE_NAME", "AUTH_SESSION_COOKIE_SECURE",
            "MAIL_PROVIDER", "MAIL_HOST", "MAIL_PORT", "MAIL_USER", "MAIL_PASSWORD", "MAIL_FROM",
            "REMINDER_HOURS_BEFORE", "RATE_LIMIT_PUBLIC_PER_MIN", "BOOKING_TOKEN_TTL_DAYS",
            "CORS_ALLOWED_ORIGINS",
            "DB_NAME");

    private static final List<String> SENSITIVE_VARIABLES = List.of(
            "DB_PASSWORD", "DB_MIGRATION_PASSWORD", "BOOTSTRAP_ADMIN_PASSWORD", "MAIL_PASSWORD");

    private static final Pattern PASSWORD_LINE =
            Pattern.compile("^\\s*([\\w.-]*password[\\w.-]*)\\s*:\\s*(\\S.*?)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLAIN_PLACEHOLDER = Pattern.compile("^\\$\\{[A-Z][A-Z0-9_]*}$");

    @Test
    void envExampleDeclaraTodasLasVariablesDeKb08() throws IOException {
        Map<String, String> variables = parseEnv(Files.readAllLines(ENV_EXAMPLE));

        assertThat(variables.keySet()).containsAll(EXPECTED_VARIABLES);
    }

    @Test
    void lasVariablesSensiblesUsanElMarcadorChangeMe() throws IOException {
        Map<String, String> variables = parseEnv(Files.readAllLines(ENV_EXAMPLE));

        assertThat(SENSITIVE_VARIABLES).allSatisfy(name -> assertThat(variables.get(name)).isEqualTo("change-me"));
    }

    @Test
    void envExampleNoDeclaraVariablesDeInfraestructuraDeSuperusuario() throws IOException {
        Map<String, String> variables = parseEnv(Files.readAllLines(ENV_EXAMPLE));

        assertThat(variables.keySet()).doesNotContain("POSTGRES_PASSWORD", "POSTGRES_USER");
    }

    @Test
    void envExampleIndicaQueChangeConsumeCadaGrupo() throws IOException {
        String content = Files.readString(ENV_EXAMPLE);

        assertThat(content).contains("C-04").contains("C-02");
    }

    @Test
    void variablesDeCookieDeSesionNoSonSensibles() throws IOException {
        Map<String, String> variables = parseEnv(Files.readAllLines(ENV_EXAMPLE));

        assertThat(variables.get("AUTH_SESSION_COOKIE_NAME")).isEqualTo("SESSIONID");
        assertThat(variables.get("AUTH_SESSION_COOKIE_SECURE")).isEqualTo("true");
        assertThat(variables.get("AUTH_SESSION_COOKIE_NAME")).doesNotContain("change-me", "password", "secret");
        assertThat(variables.get("AUTH_SESSION_COOKIE_SECURE")).doesNotContain("change-me", "password", "secret");
    }

    @Test
    void losYamlVersionadosSoloReferencianContrasenasPorVariable() throws IOException {
        List<Path> yamls;
        try (Stream<Path> files = Files.list(RESOURCES)) {
            yamls = files.filter(p -> p.getFileName().toString().matches("application.*\\.yml")).toList();
        }

        assertThat(yamls).isNotEmpty();
        for (Path yaml : yamls) {
            assertThat(literalPasswords(Files.readString(yaml)))
                    .as("contrasenas literales o con valor por defecto en %s", yaml)
                    .isEmpty();
        }
    }

    @Test
    void laDeteccionDeContrasenasLiteralesFunciona() {
        String yaml = """
                spring:
                  datasource:
                    password: secreto123
                    other-password: ${OTRA:por-defecto}
                    ok-password: ${DB_PASSWORD}
                """;

        assertThat(literalPasswords(yaml)).hasSize(2).anyMatch(l -> l.contains("secreto123"));
    }

    @Test
    void elScriptDeRolesNoMuestraContrasenasEnPantalla() throws IOException {
        String script = Files.readString(ROLES_SCRIPT);

        assertThat(printedStatementsWithPasswords(script))
                .as("sentencias de %s cuyo resultado psql imprime y que incluyen contrasenas", ROLES_SCRIPT)
                .isEmpty();
    }

    @Test
    void laDeteccionDeContrasenasImpresasFunciona() {
        String script = """
                -- comentario con :'app_password' que no cuenta
                SELECT :'db_name' AS db_name, :'app_password' AS app_password;
                SELECT format('ALTER ROLE %I PASSWORD %L', :'app_user', :'app_password')
                \\gexec
                SELECT 'ok' WHERE :'migrator_password' <> '';
                """;

        assertThat(printedStatementsWithPasswords(script))
                .hasSize(1)
                .allMatch(statement -> statement.contains("AS app_password"));
    }

    /**
     * psql imprime el resultado de las sentencias terminadas en {@code ;}; las terminadas en {@code \gexec} se
     * ejecutan sin mostrar su resultado. Devuelve las primeras que referencian una variable de contrasena en su
     * lista de resultados (un {@code WHERE} no se imprime, por eso solo se mira lo previo a {@code FROM}/{@code WHERE}).
     */
    private static List<String> printedStatementsWithPasswords(String script) {
        List<String> offenders = new ArrayList<>();
        StringBuilder statement = new StringBuilder();
        for (String line : script.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            if (trimmed.equals("\\gexec")) {
                statement.setLength(0);
                continue;
            }
            statement.append(trimmed).append(' ');
            if (trimmed.endsWith(";")) {
                String sql = statement.toString();
                String selectList = sql.split("(?i)\\b(FROM|WHERE)\\b", 2)[0];
                if (PASSWORD_VARIABLE.matcher(selectList).find()) {
                    offenders.add(sql.strip());
                }
                statement.setLength(0);
            }
        }
        return offenders;
    }

    private static Map<String, String> parseEnv(List<String> lines) {
        Map<String, String> variables = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator > 0) {
                variables.put(trimmed.substring(0, separator).strip(), trimmed.substring(separator + 1).strip());
            }
        }
        return variables;
    }

    private static List<String> literalPasswords(String yaml) {
        List<String> offenders = new ArrayList<>();
        for (String line : yaml.split("\\R")) {
            Matcher matcher = PASSWORD_LINE.matcher(line);
            if (matcher.matches() && !PLAIN_PLACEHOLDER.matcher(matcher.group(2)).matches()) {
                offenders.add(line.strip());
            }
        }
        return offenders;
    }
}
