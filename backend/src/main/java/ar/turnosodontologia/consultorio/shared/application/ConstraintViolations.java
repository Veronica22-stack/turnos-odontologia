package ar.turnosodontologia.consultorio.shared.application;

import java.sql.SQLException;

/**
 * Detecta violaciones de restricciones de exclusion de PostgreSQL por SQLState y nombre de restriccion, sin exponer
 * nunca el mensaje de la base (design D10).
 */
public final class ConstraintViolations {

    private static final String EXCLUSION_VIOLATION_SQLSTATE = "23P01";

    private ConstraintViolations() {
    }

    /** {@code true} si alguna causa es una {@code SQLException} 23P01 cuyo mensaje nombra la restriccion. */
    public static boolean isExclusionViolation(Throwable error, String constraintName) {
        for (Throwable current = error; current != null; current = current.getCause() == current ? null : current.getCause()) {
            if (current instanceof SQLException sql
                    && EXCLUSION_VIOLATION_SQLSTATE.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(constraintName)) {
                return true;
            }
        }
        return false;
    }
}
