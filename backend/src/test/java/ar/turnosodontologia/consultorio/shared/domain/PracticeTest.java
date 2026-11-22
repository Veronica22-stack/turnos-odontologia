package ar.turnosodontologia.consultorio.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PracticeTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Test
    void existingReconstruyeConsultorioConTodosLosParametros() {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio Odontológico",
                "America/Argentina/Buenos_Aires",
                "contacto@consultorio.com",
                "+541123456789",
                "Av. Corrientes 1234",
                15,
                120,
                30,
                24);

        assertThat(practice.id()).isEqualTo(PRACTICE_ID);
        assertThat(practice.name()).isEqualTo("Consultorio Odontológico");
        assertThat(practice.timezone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(practice.contactEmail()).isEqualTo("contacto@consultorio.com");
        assertThat(practice.contactPhone()).isEqualTo("+541123456789");
        assertThat(practice.address()).isEqualTo("Av. Corrientes 1234");
        assertThat(practice.slotGranularityMin()).isEqualTo(15);
        assertThat(practice.minNoticeMin()).isEqualTo(120);
        assertThat(practice.maxAdvanceDays()).isEqualTo(30);
        assertThat(practice.reminderHoursBefore()).isEqualTo(24);
    }

    @Test
    void updateSettingsModificaLosValoresCorrectamente() {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        practice.updateSettings(
                "Nuevo Nombre",
                "America/Argentina/Buenos_Aires",
                "info@dental.com",
                "1122334455",
                "Calle Falsa 123",
                30,
                60,
                45,
                48);

        assertThat(practice.name()).isEqualTo("Nuevo Nombre");
        assertThat(practice.contactEmail()).isEqualTo("info@dental.com");
        assertThat(practice.contactPhone()).isEqualTo("1122334455");
        assertThat(practice.address()).isEqualTo("Calle Falsa 123");
        assertThat(practice.slotGranularityMin()).isEqualTo(30);
        assertThat(practice.minNoticeMin()).isEqualTo(60);
        assertThat(practice.maxAdvanceDays()).isEqualTo(45);
        assertThat(practice.reminderHoursBefore()).isEqualTo(48);
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 15, 20, 30, 60})
    void granularidadesValidasSonAceptadas(int granularity) {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        practice.updateSettings("Consultorio", "America/Argentina/Buenos_Aires", null, null, null, granularity, 120, 30, 24);
        assertThat(practice.slotGranularityMin()).isEqualTo(granularity);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5, 25, 45, 90})
    void granularidadesInvalidasLanzanExcepcion(int invalidGranularity) {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "Consultorio", "America/Argentina/Buenos_Aires", null, null, null, invalidGranularity, 120, 30, 24))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("slot_granularity_min");
                });
    }

    @Test
    void minNoticeMinNegativoLanzaExcepcion() {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "Consultorio", "America/Argentina/Buenos_Aires", null, null, null, 15, -1, 30, 24))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("min_notice_min");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 366, -10})
    void maxAdvanceDaysFueraDeRangoLanzaExcepcion(int days) {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "Consultorio", "America/Argentina/Buenos_Aires", null, null, null, 15, 120, days, 24))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("max_advance_days");
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 169, -5})
    void reminderHoursBeforeFueraDeRangoLanzaExcepcion(int hours) {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "Consultorio", "America/Argentina/Buenos_Aires", null, null, null, 15, 120, 30, hours))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("reminder_hours_before");
                });
    }

    @Test
    void nombreVacioLanzaExcepcion() {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "   ", "America/Argentina/Buenos_Aires", null, null, null, 15, 120, 30, 24))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("name");
                });
    }

    @Test
    void timezoneInvalidaLanzaExcepcion() {
        Practice practice = Practice.existing(
                PRACTICE_ID,
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThatThrownBy(() -> practice.updateSettings(
                        "Consultorio", "ZonaInvalida/Inexistente", null, null, null, 15, 120, 30, 24))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.field()).isEqualTo("timezone");
                });
    }
}
