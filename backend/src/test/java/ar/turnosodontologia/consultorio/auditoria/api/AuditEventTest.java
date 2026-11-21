package ar.turnosodontologia.consultorio.auditoria.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Contrato del evento publico de auditoria: inmutable y sin campos falsificables por el reportante. */
class AuditEventTest {

    private static final UUID USER = UUID.fromString("01900000-0000-7000-8000-0000000000a1");
    private static final UUID PATIENT = UUID.fromString("01900000-0000-7000-8000-0000000000b2");

    @Test
    void eventoDeLecturaDeHistoriaClinicaEsInmutableYNoExponePracticeIdNiOccurredAt() {
        Map<String, Object> details = Map.of("outcome", "OK");

        AuditEvent event = AuditEvent.of(AuditAction.HC_READ, USER, PATIENT, "hc_entry", "entrada-123",
                "192.168.1.10", "Mozilla/5.0", details);

        assertThat(event.action()).isEqualTo(AuditAction.HC_READ);
        assertThat(event.userId()).isEqualTo(USER);
        assertThat(event.patientId()).isEqualTo(PATIENT);
        assertThat(event.entityType()).isEqualTo("hc_entry");
        assertThat(event.entityId()).isEqualTo("entrada-123");
        assertThat(event.ip()).isEqualTo("192.168.1.10");
        assertThat(event.userAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.details()).containsExactlyInAnyOrderEntriesOf(details);
        assertThat(AuditEvent.class.isRecord()).isTrue();
        assertThat(Arrays.stream(AuditEvent.class.getRecordComponents()).map(RecordComponent::getName))
                .doesNotContain("practiceId", "occurredAt");
        for (Field field : AuditEvent.class.getDeclaredFields()) {
            assertThat(Modifier.isPrivate(field.getModifiers())).as("campo privado %s", field.getName()).isTrue();
            assertThat(Modifier.isFinal(field.getModifiers())).as("campo final %s", field.getName()).isTrue();
        }
        assertThatThrownBy(() -> event.details().put("outcome", "ALTERADO"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ipConFormatoInvalidoSeNormalizaANuloSinRechazarElEvento() {
        AuditEvent event = AuditEvent.of(AuditAction.PATIENT_READ, USER, null, "patient", "paciente-1",
                "no-es-una-ip", "agente", Map.of());

        assertThat(event.ip()).isNull();
        assertThat(event.action()).isEqualTo(AuditAction.PATIENT_READ);
    }

    @Test
    void eventoAnonimoDeTokenSinUsuarioNiIpEsValidoYNoMuestraDetallesEnToString() {
        AuditEvent event = AuditEvent.of(AuditAction.BOOKING_TOKEN_USE, null, null, "booking", "turno-456",
                null, null, Map.of("source", "texto-clinico-secreto", "outcome", "OK"));

        assertThat(event.userId()).isNull();
        assertThat(event.ip()).isNull();
        assertThat(event.action()).isEqualTo(AuditAction.BOOKING_TOKEN_USE);
        assertThat(event.toString()).doesNotContain("texto-clinico-secreto");
        assertThat(event.toString()).contains("BOOKING_TOKEN_USE").contains("turno-456");
    }

    @Test
    void ipV6ValidoSeConserva() {
        AuditEvent event = AuditEvent.of(AuditAction.LOGIN_FAIL, USER, null, "session", "s-2",
                "2001:db8::1", "agente", null);

        assertThat(event.ip()).isEqualTo("2001:db8::1");
        assertThat(event.details()).isEmpty();
    }

    @Test
    void userAgentMayorA512CaracteresEsRechazado() {
        String tooLong = "a".repeat(513);

        assertThatThrownBy(() -> AuditEvent.of(AuditAction.LOGIN_OK, USER, null, "session", "s-1",
                "127.0.0.1", tooLong, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
