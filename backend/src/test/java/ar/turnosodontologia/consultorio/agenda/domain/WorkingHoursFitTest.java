package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkingHoursFitTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    /** 2026-12-10 es jueves (weekday ISO 4); Buenos Aires es UTC-3 todo el ano. */
    private static final int THURSDAY = 4;

    private static WorkingHours slot(int weekday, String start, String end) {
        return WorkingHours.create(PRACTICE_ID, weekday, LocalTime.parse(start), LocalTime.parse(end));
    }

    /** Instante UTC de una hora local de Buenos Aires del 2026-12-10. */
    private static Instant local(String time) {
        return Instant.parse("2026-12-10T" + time + ":00-03:00");
    }

    private static boolean fits(String start, String end, WorkingHours... hours) {
        return WorkingHoursFit.fits(local(start), local(end), BUENOS_AIRES, List.of(hours));
    }

    // ---- tramo unico (3.7) ----

    @Test
    void cabeDentroDelTramoIncluidosLosBordesExactos() {
        WorkingHours morning = slot(THURSDAY, "09:00", "13:00");

        assertThat(fits("10:00", "10:30", morning)).isTrue();
        assertThat(fits("09:00", "09:30", morning)).isTrue();
        assertThat(fits("12:30", "13:00", morning)).isTrue();
        assertThat(fits("09:00", "13:00", morning)).isTrue();
    }

    @Test
    void noCabeSiExcedeElFinOEmpiezaAntesDelInicio() {
        WorkingHours morning = slot(THURSDAY, "09:00", "13:00");

        assertThat(fits("12:45", "13:15", morning)).isFalse();
        assertThat(fits("08:45", "09:15", morning)).isFalse();
        assertThat(fits("13:00", "13:30", morning)).isFalse();
        assertThat(fits("07:00", "08:00", morning)).isFalse();
    }

    @Test
    void unDiaSinTramosOConTramosDeOtroDiaNoAdmiteTurnos() {
        assertThat(fits("10:00", "10:30")).isFalse();
        assertThat(fits("10:00", "10:30", slot(THURSDAY + 1, "09:00", "13:00"))).isFalse();
    }

    @Test
    void unTurnoQueCruzaLaMedianocheLocalNuncaCabe() {
        WorkingHours night = slot(THURSDAY, "20:00", "23:59");
        WorkingHours nextDay = slot(THURSDAY + 1, "00:00", "02:00");
        Instant start = local("23:45");
        Instant end = Instant.parse("2026-12-11T00:15:00-03:00");

        assertThat(WorkingHoursFit.fits(start, end, BUENOS_AIRES, List.of(night, nextDay))).isFalse();
    }

    // ---- fusion de tramos contiguos (3.9) ----

    @Test
    void unTurnoPuedeAtravesarTramosContiguos() {
        WorkingHours first = slot(THURSDAY, "09:00", "12:00");
        WorkingHours second = slot(THURSDAY, "12:00", "15:00");

        assertThat(fits("11:30", "12:30", first, second)).isTrue();
    }

    @Test
    void unTurnoNoPuedeAtravesarUnHuecoEntreTramos() {
        WorkingHours first = slot(THURSDAY, "09:00", "12:00");
        WorkingHours second = slot(THURSDAY, "13:00", "15:00");

        assertThat(fits("11:30", "12:30", first, second)).isFalse();
        assertThat(fits("11:30", "12:00", first, second)).isTrue();
    }

    @Test
    void losBordesDelIntervaloFusionadoSonExactos() {
        WorkingHours first = slot(THURSDAY, "09:00", "12:00");
        WorkingHours second = slot(THURSDAY, "12:00", "15:00");

        assertThat(fits("09:00", "09:30", first, second)).isTrue();
        assertThat(fits("14:30", "15:00", first, second)).isTrue();
        assertThat(fits("14:45", "15:15", first, second)).isFalse();
        assertThat(fits("08:45", "09:15", first, second)).isFalse();
    }

    @Test
    void fusionaTramosDesordenadosYEncadenados() {
        WorkingHours third = slot(THURSDAY, "14:00", "16:00");
        WorkingHours first = slot(THURSDAY, "09:00", "12:00");
        WorkingHours second = slot(THURSDAY, "12:00", "14:00");

        assertThat(fits("11:30", "14:30", third, first, second)).isTrue();
        assertThat(fits("09:00", "16:00", third, first, second)).isTrue();
        assertThat(fits("15:30", "16:30", third, first, second)).isFalse();
    }

    @Test
    void noFusionaTramosDeOtroWeekday() {
        WorkingHours thursday = slot(THURSDAY, "09:00", "12:00");
        WorkingHours friday = slot(THURSDAY + 1, "12:00", "15:00");

        assertThat(fits("11:30", "12:30", thursday, friday)).isFalse();
    }

    @Test
    void convierteDeUtcALaZonaDelConsultorio() {
        WorkingHours morning = slot(THURSDAY, "09:00", "13:00");

        // 12:00Z = 09:00 locales (cabe); 11:30Z = 08:30 locales (fuera)
        assertThat(WorkingHoursFit.fits(Instant.parse("2026-12-10T12:00:00Z"), Instant.parse("2026-12-10T12:30:00Z"),
                BUENOS_AIRES, List.of(morning))).isTrue();
        assertThat(WorkingHoursFit.fits(Instant.parse("2026-12-10T11:30:00Z"), Instant.parse("2026-12-10T12:00:00Z"),
                BUENOS_AIRES, List.of(morning))).isFalse();
    }
}
