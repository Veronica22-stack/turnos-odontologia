package ar.turnosodontologia.consultorio.auditoria.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogCommand;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogEntry;
import ar.turnosodontologia.consultorio.auditoria.domain.AuditLogRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** El caso de uso fija practice_id y occurred_at con datos del servidor, nunca con datos del reportante. */
@ExtendWith(OutputCaptureExtension.class)
class AuditLoggerServiceTest {

    private static final UUID USER = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID PATIENT = UUID.fromString("01900000-0000-7000-8000-0000000000b2");
    private static final UUID PRACTICE = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final Instant FIXED = Instant.parse("2026-03-11T02:30:00Z");

    private final List<AuditLogEntry> saved = new ArrayList<>();
    private final AuditLogRepository repository = saved::add;

    private static InstallationPractice installation(UUID practiceId) {
        PracticeRepository practices = () -> List.of(
                Practice.existing(practiceId, "Consultorio", "America/Argentina/Buenos_Aires"));
        return new InstallationPractice(practices);
    }

    @Test
    void elRegistroLlevaPracticeIdYOccurredAtDelServidorYDetailsSanitizado() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("outcome", "OK");
        details.put("email", "dr@ejemplo.com");
        details.put("dni", "30123456");

        new AuditLoggerService(repository, installation(PRACTICE), Clock.fixed(FIXED, ZoneOffset.UTC))
                .record(new AuditLogCommand("HC_READ", USER, PATIENT, "hc_entry", "entrada-123",
                        "192.168.1.10", "agente", details));

        assertThat(saved).hasSize(1);
        AuditLogEntry entry = saved.get(0);
        assertThat(entry.practiceId()).isEqualTo(PRACTICE);
        assertThat(entry.occurredAt()).isEqualTo(FIXED);
        assertThat(entry.action()).isEqualTo("HC_READ");
        assertThat(entry.userId()).isEqualTo(USER);
        assertThat(entry.patientId()).isEqualTo(PATIENT);
        assertThat(entry.details()).containsOnlyKeys("outcome");
        assertThat(entry.details().toString()).doesNotContain("dr@ejemplo.com", "30123456");
    }

    @Test
    void otroConsultorioYOtroRelojSeReflejanEnUnEventoAnonimo() {
        UUID otherPractice = UUID.fromString("01900000-0000-7000-8000-0000000000c3");
        Instant otherInstant = Instant.parse("2026-11-21T23:59:59Z");

        new AuditLoggerService(repository, installation(otherPractice), Clock.fixed(otherInstant, ZoneOffset.UTC))
                .record(new AuditLogCommand("BOOKING_TOKEN_USE", null, null, "booking", "turno-789",
                        null, null, Map.of("outcome", "OK")));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).practiceId()).isEqualTo(otherPractice);
        assertThat(saved.get(0).occurredAt()).isEqualTo(otherInstant);
        assertThat(saved.get(0).userId()).isNull();
        assertThat(saved.get(0).action()).isEqualTo("BOOKING_TOKEN_USE");
    }

    @Test
    void falloDeEscrituraEntregaErrorGenericoSinDatosPersonalesYNadaDeDatosClinicos(CapturedOutput output) {
        AtomicBoolean clinicalDataDelivered = new AtomicBoolean(false);
        AuditLogRepository failing = entry -> {
            throw new IllegalStateException("insert fallo para dr@ejemplo.com con evolucion 30123456");
        };
        AuditLoggerService service =
                new AuditLoggerService(failing, installation(PRACTICE), Clock.fixed(FIXED, ZoneOffset.UTC));

        assertThatThrownBy(() -> {
            service.record(new AuditLogCommand("HC_READ", USER, PATIENT, "hc_entry", "entrada-123",
                    "192.168.1.10", "agente", Map.of("outcome", "evolucion-clinica-confidencial")));
            clinicalDataDelivered.set(true);
        })
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("AUDIT_WRITE_FAILED")
                .extracting(exception -> ((BusinessRuleException) exception).code())
                .isEqualTo("AUDIT_WRITE_FAILED");

        assertThat(clinicalDataDelivered).isFalse();
        assertThat(output.toString()).doesNotContain("dr@ejemplo.com", "30123456", "insert fallo",
                "evolucion-clinica-confidencial");
    }
}
