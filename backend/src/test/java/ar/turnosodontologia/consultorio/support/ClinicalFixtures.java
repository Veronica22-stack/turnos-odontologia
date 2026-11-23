package ar.turnosodontologia.consultorio.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Limpieza de datos clinicos para los ITs; escribe con el rol de migracion (el rol de la app no puede borrar). */
public final class ClinicalFixtures {

    private ClinicalFixtures() {
    }

    /**
     * Vacia {@code evolution_entry}. La autorreferencia {@code corrects_entry_id} (ON DELETE RESTRICT) impide borrar
     * una cadena A <- B <- C de una sola vez: se borran repetidamente las hojas (entradas que nadie corrige) hasta
     * vaciar la tabla. Debe llamarse antes de borrar {@code clinical_record}, {@code appointment} o pacientes.
     */
    public static void deleteEvolutions() {
        try (Connection connection = AbstractIntegrationTest.DATABASE.migratorConnection();
                Statement statement = connection.createStatement()) {
            int deleted;
            do {
                deleted = statement.executeUpdate("DELETE FROM evolution_entry WHERE id NOT IN "
                        + "(SELECT corrects_entry_id FROM evolution_entry WHERE corrects_entry_id IS NOT NULL)");
            } while (deleted > 0);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }
}
