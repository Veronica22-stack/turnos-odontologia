package ar.turnosodontologia.consultorio.auditoria.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.api.AuditAction;
import ar.turnosodontologia.consultorio.auditoria.api.AuditEvent;
import ar.turnosodontologia.consultorio.auditoria.api.AuditLogger;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogRepository;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Ni los valores personales ni el mensaje de una excepcion llegan a la fila ni a la salida (design D3/D6). */
@ExtendWith(OutputCaptureExtension.class)
@Import(AuditLogLeakageIT.FailingRepositoryConfiguration.class)
class AuditLogLeakageIT extends AbstractIntegrationTest {

    private static final String DNI = "30123456";
    private static final String EMAIL = "dr@ejemplo.com";
    private static final String PHONE = "1555123456";
    private static final String NAME = "Dr. Perez";
    private static final String CLINICAL = "evolucion reservada del paciente";
    private static final String TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV";

    private static final AtomicBoolean FAILING = new AtomicBoolean(false);

    private final AuditLogger auditLogger;

    AuditLogLeakageIT(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    @BeforeEach
    void resetFake() {
        FAILING.set(false);
    }

    @AfterEach
    void cleanRows() throws SQLException {
        FAILING.set(false);
        try (Connection connection = DATABASE.migratorConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM audit_log");
        }
    }

    @Test
    void losDatosPersonalesNoLleganALaFilaNiALaSalida(CapturedOutput output) throws SQLException {
        String entityId = "safe-" + UUID.randomUUID();
        AuditEvent event = AuditEvent.of(AuditAction.HC_READ, null, null,
                "historia_clinica", entityId, null, null, Map.of(
                        "dni", DNI,
                        "email", EMAIL,
                        "phone", PHONE,
                        "full_name", NAME,
                        "clinical_text", CLINICAL,
                        "source", TOKEN));

        auditLogger.log(event);

        String details = storedDetails(entityId);
        assertThat(details).doesNotContain(DNI, EMAIL, PHONE, NAME, CLINICAL, TOKEN,
                "dni", "email", "phone", "full_name", "clinical_text");
        assertThat(output.toString()).doesNotContain(DNI, EMAIL, PHONE, NAME, CLINICAL, TOKEN);
    }

    @Test
    void laExcepcionConDatosPersonalesNoSePropagaALaSalida(CapturedOutput output) {
        FAILING.set(true);

        assertThatThrownBy(() -> auditLogger.log(AuditEvent.of(AuditAction.HC_CORRECT, null, null,
                "historia_clinica", "correccion-" + UUID.randomUUID(), null, null,
                Map.of("description", CLINICAL))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("AUDIT_WRITE_FAILED")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain(
                        DNI, EMAIL, PHONE, NAME, CLINICAL, TOKEN));

        assertThat(output.toString()).doesNotContain(DNI, EMAIL, PHONE, NAME, CLINICAL, TOKEN);
    }

    private static String storedDetails(String entityId) throws SQLException {
        try (Connection connection = DATABASE.migratorConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT details::text FROM audit_log WHERE entity_id = ?")) {
            select.setString(1, entityId);
            try (ResultSet rows = select.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return rows.getString(1);
            }
        }
    }

    /** Falso conmutador: por defecto delega en el adaptador real; en el caso de fallo lanza con PII en el mensaje. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FailingRepositoryConfiguration {

        @Bean
        @Primary
        AuditLogRepository failingRepository(@Qualifier("jdbcAuditLogRepository") AuditLogRepository real) {
            return entry -> {
                if (FAILING.get()) {
                    throw new IllegalStateException("Fallo con " + DNI + " " + EMAIL + " " + PHONE + " "
                            + NAME + " " + CLINICAL + " " + TOKEN);
                }
                real.save(entry);
            };
        }
    }
}
