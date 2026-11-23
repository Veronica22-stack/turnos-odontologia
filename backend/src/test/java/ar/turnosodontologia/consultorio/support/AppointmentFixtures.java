package ar.turnosodontologia.consultorio.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Datos de prueba para los ITs de turnos. Escribe con el rol de migracion porque el rol de aplicacion no puede
 * borrar turnos (V13): los ITs que crean turnos limpian con {@link #cleanAll()} en {@code @AfterEach}.
 */
public final class AppointmentFixtures {

    public static final String PRACTICE_ID = "01900000-0000-7000-8000-000000000001";
    private static final String MARK = "IT-APPT";

    private AppointmentFixtures() {
    }

    public static UUID patient(boolean active) {
        UUID id = UUID.randomUUID();
        String dni = Long.toString(Math.abs(id.getMostSignificantBits()) % 90_000_000L + 10_000_000L);
        execute("INSERT INTO patient (id, practice_id, first_name, last_name, dni, phone, active) VALUES ('" + id
                + "', '" + PRACTICE_ID + "', '" + MARK + "', 'Paciente', '" + dni + "', '1100000000', " + active
                + ")");
        return id;
    }

    public static UUID service(int durationMin, boolean active, boolean publicVisible) {
        UUID id = UUID.randomUUID();
        execute("INSERT INTO service (id, practice_id, name, duration_min, active, public_visible) VALUES ('" + id
                + "', '" + PRACTICE_ID + "', '" + MARK + "-" + id + "', " + durationMin + ", " + active + ", "
                + publicVisible + ")");
        return id;
    }

    /** Tramo de atencion en hora local (formato {@code HH:mm}). */
    public static void workingHours(int weekday, String start, String end) {
        execute("INSERT INTO working_hours (id, practice_id, weekday, start_time, end_time) VALUES ('"
                + UUID.randomUUID() + "', '" + PRACTICE_ID + "', " + weekday + ", '" + start + "', '" + end + "')");
    }

    public static void scheduleBlock(String startsAt, String endsAt) {
        execute("INSERT INTO schedule_block (id, practice_id, starts_at, ends_at, reason) VALUES ('"
                + UUID.randomUUID() + "', '" + PRACTICE_ID + "', '" + startsAt + "', '" + endsAt + "', 'IT-APPT')");
    }

    public static void updateServiceDuration(UUID serviceId, int durationMin, boolean active) {
        execute("UPDATE service SET duration_min = " + durationMin + ", active = " + active + " WHERE id = '"
                + serviceId + "'");
    }

    /** Inserta un turno directo en la base (sin pasar por el dominio), para armar escenarios en cualquier estado. */
    public static UUID appointment(UUID patientId, UUID serviceId, String startsAt, String endsAt, String status) {
        UUID id = UUID.randomUUID();
        boolean cancelled = "CANCELLED".equals(status);
        execute("INSERT INTO appointment (id, practice_id, patient_id, service_id, starts_at, ends_at, "
                + "duration_min_snapshot, status, origin, cancelled_by, cancelled_at) VALUES ('" + id + "', '"
                + PRACTICE_ID + "', '" + patientId + "', '" + serviceId + "', '" + startsAt + "', '" + endsAt
                + "', 30, '" + status + "', 'INTERNAL', " + (cancelled ? "'DENTIST', now()" : "NULL, NULL") + ")");
        return id;
    }

    public static int countAppointments() {
        try (Connection connection = AbstractIntegrationTest.DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rows = statement.executeQuery("SELECT count(*) FROM appointment")) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Ejecuta un {@code SELECT count(*) ...} con el rol de migracion. */
    public static int count(String sql) {
        try (Connection connection = AbstractIntegrationTest.DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    public static String statusOf(UUID appointmentId) {
        try (Connection connection = AbstractIntegrationTest.DATABASE.migratorConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rows = statement.executeQuery(
                        "SELECT status FROM appointment WHERE id = '" + appointmentId + "'")) {
            return rows.next() ? rows.getString(1) : null;
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }

    /** Borra todo lo que crean estos fixtures (notificaciones, turnos, bloqueos, horarios, pacientes y prestaciones de prueba). */
    public static void cleanAll() {
        // notification referencia appointment con ON DELETE RESTRICT (V14): se vacia antes que los turnos.
        // evolution_entry referencia appointment y clinical_record con ON DELETE RESTRICT (V15).
        ClinicalFixtures.deleteEvolutions();
        execute("DELETE FROM notification");
        // booking_token referencia appointment con ON DELETE RESTRICT (V16).
        execute("DELETE FROM booking_token");
        execute("DELETE FROM appointment");
        execute("DELETE FROM schedule_block");
        execute("DELETE FROM working_hours");
        execute("DELETE FROM patient WHERE first_name = '" + MARK + "'");
        execute("DELETE FROM service WHERE name LIKE '" + MARK + "-%'");
    }

    public static void execute(String sql) {
        try (Connection connection = AbstractIntegrationTest.DATABASE.migratorConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (SQLException error) {
            throw new IllegalStateException(error);
        }
    }
}
