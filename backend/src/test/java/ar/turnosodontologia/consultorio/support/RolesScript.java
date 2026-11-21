package ar.turnosodontologia.consultorio.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intérprete del dialecto acotado de psql que usa {@code infra/postgres/init/01-roles.sql} (design.md D6/D7), para
 * poder ejecutar en los tests el MISMO script que se corre a mano con psql.
 *
 * <p>Dialecto soportado: comentarios {@code --}, sentencias terminadas en {@code ;}, sentencias terminadas en la
 * línea {@code \gexec} (ejecuta cada fila devuelta como sentencia), el metacomando {@code \connect} y las referencias
 * {@code :'var'} (literal) y {@code :"var"} (identificador). Cualquier otro metacomando se rechaza. No soporta
 * {@code $$} ni comentarios de bloque.
 *
 * <p>Los mensajes de error nunca incluyen el SQL ya sustituido (puede contener contraseñas): solo el texto original.
 */
public final class RolesScript {

    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private RolesScript() {
    }

    /** Un paso del script ya con las variables sustituidas. */
    public sealed interface Step permits Statement, Gexec, Connect {
    }

    /** Sentencia SQL simple; {@code source} es el texto original (sin sustituir). */
    public record Statement(String sql, String source) implements Step {
    }

    /** Sentencia cuyo resultado (primera columna de cada fila) se ejecuta como sentencias. */
    public record Gexec(String sql, String source) implements Step {
    }

    /** Cambia la conexión a otra base. */
    public record Connect(String database) implements Step {
    }

    /** Fábrica de conexiones JDBC (autocommit) por nombre de base. */
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open(String database) throws SQLException;
    }

    /** Parsea todo el script (y sustituye todas las variables) antes de ejecutar nada. */
    public static List<Step> parse(String script, Map<String, String> variables) {
        List<Step> steps = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        StringBuilder source = new StringBuilder();
        int length = script.length();
        int i = 0;
        while (i < length) {
            char current = script.charAt(i);
            if (current == '-' && i + 1 < length && script.charAt(i + 1) == '-') {
                i = endOfLine(script, i);
            } else if (current == '\'' || current == '"') {
                int end = endOfQuoted(script, i);
                String quoted = script.substring(i, end);
                sql.append(quoted);
                source.append(quoted);
                i = end;
            } else if (current == '\\' && isFirstOnLine(script, i)) {
                int end = endOfLine(script, i);
                handleMetacommand(script.substring(i, end).strip(), sql, source, steps, variables);
                i = end;
            } else if (isVariableReference(script, i)) {
                int[] consumed = new int[1];
                sql.append(substituteReference(script, i, variables, consumed));
                source.append(script, i, consumed[0]);
                i = consumed[0];
            } else if (current == ';') {
                flush(steps, sql, source, false);
                i++;
            } else {
                sql.append(current);
                source.append(current);
                i++;
            }
        }
        if (!sql.toString().isBlank()) {
            throw new IllegalArgumentException("Sentencia sin terminar al final del script: " + source.toString().strip());
        }
        return steps;
    }

    /** Ejecuta el script contra una instancia PostgreSQL, empezando conectado a {@code initialDatabase}. */
    public static void execute(
            String script, Map<String, String> variables, String initialDatabase, ConnectionFactory connections)
            throws SQLException {
        List<Step> steps = parse(script, variables);
        Connection connection = connections.open(initialDatabase);
        try {
            for (Step step : steps) {
                if (step instanceof Connect connect) {
                    connection.close();
                    connection = connections.open(connect.database());
                } else if (step instanceof Statement statement) {
                    run(connection, statement.sql(), statement.source());
                } else if (step instanceof Gexec gexec) {
                    for (String generated : query(connection, gexec)) {
                        run(connection, generated, "sentencia generada por \\gexec desde: " + gexec.source());
                    }
                }
            }
        } finally {
            connection.close();
        }
    }

    private static List<String> query(Connection connection, Gexec gexec) throws SQLException {
        List<String> generated = new ArrayList<>();
        try (java.sql.Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(gexec.sql())) {
            while (rows.next()) {
                generated.add(rows.getString(1));
            }
        } catch (SQLException error) {
            throw failure(gexec.source(), error);
        }
        return generated;
    }

    private static void run(Connection connection, String sql, String source) throws SQLException {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException error) {
            throw failure(source, error);
        }
    }

    private static SQLException failure(String source, SQLException error) {
        return new SQLException(
                "Falló el script de roles en [" + source + "]: " + error.getMessage(), error.getSQLState(), error);
    }

    private static void handleMetacommand(
            String command, StringBuilder sql, StringBuilder source, List<Step> steps, Map<String, String> variables) {
        String name = command.split("\\s+", 2)[0];
        switch (name) {
            case "\\gexec" -> {
                if (!command.equals("\\gexec")) {
                    throw new IllegalArgumentException("\\gexec no admite argumentos: " + command);
                }
                if (sql.toString().isBlank()) {
                    throw new IllegalArgumentException("\\gexec sin una sentencia previa");
                }
                flush(steps, sql, source, true);
            }
            case "\\connect" -> {
                if (!sql.toString().isBlank()) {
                    throw new IllegalArgumentException("Sentencia sin terminar antes de \\connect: " + source.toString().strip());
                }
                String argument = command.substring(name.length()).strip();
                if (argument.isEmpty()) {
                    throw new IllegalArgumentException("\\connect requiere el nombre de la base");
                }
                steps.add(new Connect(unquoteIdentifier(substituteAll(argument, variables))));
            }
            default -> throw new IllegalArgumentException("Metacomando no soportado: " + name);
        }
    }

    private static void flush(List<Step> steps, StringBuilder sql, StringBuilder source, boolean gexec) {
        String text = sql.toString().strip();
        String original = source.toString().strip();
        sql.setLength(0);
        source.setLength(0);
        if (text.isEmpty()) {
            return;
        }
        steps.add(gexec ? new Gexec(text, original) : new Statement(text, original));
    }

    private static boolean isVariableReference(String script, int index) {
        if (script.charAt(index) != ':' || index + 1 >= script.length()) {
            return false;
        }
        char next = script.charAt(index + 1);
        return (next == '\'' || next == '"') && (index == 0 || script.charAt(index - 1) != ':');
    }

    /** Sustituye la referencia que empieza en {@code index}; {@code consumed[0]} queda en el primer carácter posterior. */
    private static String substituteReference(String script, int index, Map<String, String> variables, int[] consumed) {
        char quote = script.charAt(index + 1);
        int end = script.indexOf(quote, index + 2);
        if (end < 0) {
            throw new IllegalArgumentException("Referencia a variable sin cerrar en el script");
        }
        String name = script.substring(index + 2, end);
        if (!VARIABLE_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Nombre de variable inválido en el script: " + name);
        }
        String value = variables.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Falta la variable de psql '" + name + "'");
        }
        consumed[0] = end + 1;
        return quote == '\'' ? "'" + value.replace("'", "''") + "'" : "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String substituteAll(String text, Map<String, String> variables) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (isVariableReference(text, i)) {
                int[] consumed = new int[1];
                result.append(substituteReference(text, i, variables, consumed));
                i = consumed[0];
            } else {
                result.append(text.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    private static String unquoteIdentifier(String text) {
        Matcher quoted = Pattern.compile("^\"(.*)\"$").matcher(text);
        return quoted.matches() ? quoted.group(1).replace("\"\"", "\"") : text;
    }

    private static boolean isFirstOnLine(String script, int index) {
        for (int i = index - 1; i >= 0 && script.charAt(i) != '\n'; i--) {
            if (!Character.isWhitespace(script.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static int endOfLine(String script, int from) {
        int end = script.indexOf('\n', from);
        return end < 0 ? script.length() : end;
    }

    /** Fin (exclusivo) del literal o identificador entrecomillado que empieza en {@code start}; {@code ''} escapa. */
    private static int endOfQuoted(String script, int start) {
        char quote = script.charAt(start);
        int i = start + 1;
        while (i < script.length()) {
            if (script.charAt(i) == quote) {
                if (i + 1 < script.length() && script.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        throw new IllegalArgumentException("Comilla sin cerrar en el script");
    }
}
