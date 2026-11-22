package ar.turnosodontologia.consultorio.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PatientSchemaIT extends AbstractIntegrationTest {

    private static final String PRACTICE = "01900000-0000-7000-8000-000000000001";

    @AfterEach
    void cleanRows() throws SQLException {
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM patient");
        }
    }

    @Test
    void appRolePuedeInsertarYConsultarPacientes() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone, email, source, active) "
                            + "VALUES (?::uuid, ?::uuid, 'Juan', 'Perez', '12345678', '11223344', 'juan@test.com', 'MANUAL', true)")) {
                insert.setString(1, id.toString());
                insert.setString(2, PRACTICE);
                int count = insert.executeUpdate();
                assertThat(count).isEqualTo(1);
            }

            try (PreparedStatement select = connection.prepareStatement("SELECT first_name, active FROM patient WHERE id = ?::uuid")) {
                select.setString(1, id.toString());
                try (ResultSet rs = select.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("first_name")).isEqualTo("Juan");
                    assertThat(rs.getBoolean("active")).isTrue();
                }
            }
        }
    }

    @Test
    void rechazaDniDuplicadoEnMismoConsultorio() throws SQLException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            try (PreparedStatement insert1 = connection.prepareStatement(
                    "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) "
                            + "VALUES (?::uuid, ?::uuid, 'Juan', 'Perez', '88888888', '111111')")) {
                insert1.setString(1, id1.toString());
                insert1.setString(2, PRACTICE);
                insert1.executeUpdate();
            }

            try (PreparedStatement insert2 = connection.prepareStatement(
                    "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) "
                            + "VALUES (?::uuid, ?::uuid, 'Pedro', 'Gomez', '88888888', '222222')")) {
                insert2.setString(1, id2.toString());
                insert2.setString(2, PRACTICE);
                assertThatThrownBy(insert2::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("patient_practice_dni_unique");
            }
        }
    }

    @Test
    void rechazaDeleteFisicoDesdeAppRole() throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = DATABASE.appConnection()) {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone) "
                            + "VALUES (?::uuid, ?::uuid, 'Ana', 'Lopez', '99999999', '333333')")) {
                insert.setString(1, id.toString());
                insert.setString(2, PRACTICE);
                insert.executeUpdate();
            }

            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM patient WHERE id = ?::uuid")) {
                delete.setString(1, id.toString());
                assertThatThrownBy(delete::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }
}
