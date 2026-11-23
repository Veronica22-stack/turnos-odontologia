package ar.turnosodontologia.consultorio.notificaciones.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Plantillas de texto plano, en hora local del consultorio y sin datos del paciente (design D8, D11). */
class EmailTemplatesTest {

    private static final Instant STARTS_AT = Instant.parse("2026-12-10T13:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private static EmailTemplates.Data data(String link, String phone) {
        return new EmailTemplates.Data("Limpieza dental", STARTS_AT, ZONE, "Consultorio Sonrisa",
                "Av. Siempre Viva 742", phone, link);
    }

    @ParameterizedTest
    @EnumSource(NotificationType.class)
    void todoTipoMuestraFechaYHoraLocalesYLaPrestacion(NotificationType type) {
        EmailMessage message = EmailTemplates.render(type, data(null, "351-5551234"));

        assertThat(message.body())
                .contains("jueves 10/12/2026")
                .contains("10:00")
                .contains("Limpieza dental")
                .doesNotContain("13:00");
        assertThat(message.subject()).isNotBlank();
    }

    @Test
    void confirmacionTieneAsuntoYCuerpoExactosSinLink() {
        EmailMessage message = EmailTemplates.render(NotificationType.CONFIRMATION, data(null, "351-5551234"));

        assertThat(message.subject()).isEqualTo("Turno confirmado — Consultorio Sonrisa");
        assertThat(message.body()).isEqualTo("""
                Tu turno quedó confirmado.

                Fecha: jueves 10/12/2026
                Hora: 10:00
                Prestación: Limpieza dental

                Consultorio Sonrisa
                Dirección: Av. Siempre Viva 742
                Teléfono: 351-5551234

                Para cancelar o reprogramar, comunicate con el consultorio al 351-5551234.
                """);
    }

    @Test
    void cadaTipoTieneUnAsuntoPropioYUnaPlantillaPropia() {
        assertThat(EmailTemplates.render(NotificationType.REMINDER, data(null, null)).subject())
                .isEqualTo("Recordatorio de turno — Consultorio Sonrisa");
        assertThat(EmailTemplates.render(NotificationType.RESCHEDULE, data(null, null)).subject())
                .isEqualTo("Turno reprogramado — Consultorio Sonrisa");
        assertThat(EmailTemplates.render(NotificationType.CANCELLATION, data(null, null)).subject())
                .isEqualTo("Turno cancelado — Consultorio Sonrisa");
        assertThat(EmailTemplates.templateName(NotificationType.CONFIRMATION)).isEqualTo("confirmation");
        assertThat(EmailTemplates.templateName(NotificationType.DENTIST_CANCELLATION))
                .isEqualTo("dentist-cancellation");
    }

    @Test
    void elAvisoAlOdontologoNoLlevaNombreDelPacienteNiLink() {
        EmailMessage message =
                EmailTemplates.render(NotificationType.DENTIST_CANCELLATION, data("https://x.test/turno/zzz", null));

        assertThat(message.body())
                .contains("Un paciente canceló su turno del jueves 10/12/2026 10:00 (Limpieza dental)")
                .doesNotContain("/turno/");
    }

    @Test
    void sinTokenElCuerpoNoTieneLinkYPideContactarAlConsultorioConSuTelefono() {
        EmailMessage message = EmailTemplates.render(NotificationType.REMINDER, data(null, "351-5551234"));

        assertThat(message.body())
                .doesNotContain("/turno/")
                .contains("comunicate con el consultorio al 351-5551234");
    }

    @Test
    void conLinkDeGestionElCuerpoLoIncluye() {
        EmailMessage message =
                EmailTemplates.render(NotificationType.CONFIRMATION, data("https://turnos.ejemplo.com/turno/abc123", "1"));

        assertThat(message.body()).contains("https://turnos.ejemplo.com/turno/abc123");
    }

    @Test
    void consultorioSinTelefonoNiDireccionUsaTextoAlternativoSinNull() {
        EmailTemplates.Data noContact = new EmailTemplates.Data("Limpieza dental", STARTS_AT, ZONE,
                "Consultorio Sonrisa", null, null, null);

        for (NotificationType type : NotificationType.values()) {
            EmailMessage message = EmailTemplates.render(type, noContact);
            assertThat(message.body()).doesNotContain("null");
            assertThat(message.subject()).doesNotContain("null");
        }
        assertThat(EmailTemplates.render(NotificationType.CONFIRMATION, noContact).body())
                .contains("Para cancelar o reprogramar, comunicate con el consultorio.");
    }

    @Test
    void losDatosDeLaPlantillaNoTienenCamposDelPaciente() {
        // La plantilla solo recibe prestacion, horario, zona y datos del consultorio: nunca notas ni datos personales.
        assertThat(java.util.Arrays.stream(EmailTemplates.Data.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly("serviceName", "startsAt", "zone", "practiceName", "practiceAddress",
                        "practicePhone", "managementLink");
    }
}
