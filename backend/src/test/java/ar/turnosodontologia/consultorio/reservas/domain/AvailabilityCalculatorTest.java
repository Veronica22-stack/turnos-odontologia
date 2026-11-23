package ar.turnosodontologia.consultorio.reservas.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Dominio puro: ningún Spring, JPA ni puerto de otro módulo; todo en UTC con instantes fijos. */
class AvailabilityCalculatorTest {

    private static final String DAY = "2026-03-10T";

    @Test
    void variosTramosConBloqueoParcialNoCruzaElHuecoDelMediodia() {
        List<Interval> working = List.of(
                interval("09:00", "13:00"), interval("14:00", "18:00"));
        List<Interval> occupied = List.of(interval("10:00", "10:30"));

        List<Interval> slots = AvailabilityCalculator.availableSlots(
                working, occupied, minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("09:15"), instant("09:30"),
                instant("10:30"), instant("10:45"), instant("11:00"), instant("11:15"),
                instant("11:30"), instant("11:45"), instant("12:00"), instant("12:15"),
                instant("12:30"),
                instant("14:00"), instant("14:15"), instant("14:30"), instant("14:45"),
                instant("15:00"), instant("15:15"), instant("15:30"), instant("15:45"),
                instant("16:00"), instant("16:15"), instant("16:30"), instant("16:45"),
                instant("17:00"), instant("17:15"), instant("17:30"));
        assertThat(slots).allSatisfy(slot -> assertThat(Duration.between(slot.start(), slot.end()))
                .isEqualTo(minutes(30)));
        assertThat(startsOf(slots)).allSatisfy(start -> assertThat(start.isBefore(instant("13:00"))
                || !start.isBefore(instant("14:00"))).isTrue());
    }

    @Test
    void turnoConfirmadoQueDejaHuecoInsuficienteNoOfreceElInicioAnterior() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "11:00")), List.of(interval("09:00", "10:00")),
                minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("10:00"), instant("10:15"), instant("10:30"));
    }

    @Test
    void turnoYBloqueoAdyacentesNoSeConsideranSolapamiento() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "12:00")),
                List.of(interval("09:00", "10:00"), interval("10:30", "11:00")),
                minutes(30), minutes(15));

        assertThat(startsOf(slots)).contains(instant("10:00")).doesNotContain(instant("10:30"));
        assertThat(endsOf(slots)).contains(instant("10:30"));
    }

    @Test
    void bordeFinalExactoNoGeneraUnSlotQueSeCorta() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "10:00")), List.of(), minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("09:15"), instant("09:30"));
        assertThat(endsOf(slots)).containsExactly(
                instant("09:30"), instant("09:45"), instant("10:00"));
    }

    @Test
    void granularidadVeinticonDuracionDeTreintaSoloEmiteIniciosQueCaben() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "10:10")), List.of(), minutes(30), minutes(20));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("09:20"), instant("09:40"));
    }

    @Test
    void distintasDuracionesSobreElMismoTramo() {
        List<Interval> working = List.of(interval("09:00", "10:00"));

        assertThat(startsOf(AvailabilityCalculator.availableSlots(working, List.of(), minutes(15), minutes(15))))
                .containsExactly(instant("09:00"), instant("09:15"), instant("09:30"), instant("09:45"));
        assertThat(startsOf(AvailabilityCalculator.availableSlots(working, List.of(), minutes(30), minutes(15))))
                .containsExactly(instant("09:00"), instant("09:15"), instant("09:30"));
        assertThat(startsOf(AvailabilityCalculator.availableSlots(working, List.of(), minutes(60), minutes(15))))
                .containsExactly(instant("09:00"));
    }

    @Test
    void bloqueoContiguoATurnoNoDejaUnSlotFantasma() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "12:00")),
                List.of(interval("09:00", "10:00"), interval("10:00", "10:30")),
                minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("10:30"), instant("10:45"), instant("11:00"), instant("11:15"), instant("11:30"));
        assertThat(startsOf(slots)).doesNotContain(instant("10:00"));
    }

    @Test
    void solapamientoTotalDelTramoNoOfreceSlots() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "11:00")),
                List.of(interval("08:30", "11:30")), minutes(30), minutes(15));

        assertThat(slots).isEmpty();
    }

    @Test
    void bloqueosSolapadosSeFusionanAntesDeRestar() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "13:00")),
                List.of(interval("11:00", "11:30"), interval("09:30", "10:30"),
                        interval("10:00", "11:15")),
                minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("11:30"), instant("11:45"),
                instant("12:00"), instant("12:15"), instant("12:30"));
    }

    @Test
    void tramoSinHuecoSuficienteParaLaDuracionNoOfreceSlots() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "09:50")), List.of(), minutes(60), minutes(15));

        assertThat(slots).isEmpty();
    }

    @Test
    void duracionNoMultiploDelPasoConservaLaGranularidad() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "11:00")), List.of(), minutes(25), minutes(10));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("09:10"), instant("09:20"), instant("09:30"),
                instant("09:40"), instant("09:50"), instant("10:00"), instant("10:10"),
                instant("10:20"), instant("10:30"));
        assertThat(slots).allSatisfy(slot -> assertThat(Duration.between(slot.start(), slot.end()))
                .isEqualTo(minutes(25)));
    }

    @Test
    void tramosAdyacentesSeTratanComoUnUnicoTramoLibre() {
        List<Interval> slots = AvailabilityCalculator.availableSlots(
                List.of(interval("09:00", "10:00"), interval("10:00", "11:00")),
                List.of(), minutes(30), minutes(15));

        assertThat(startsOf(slots)).containsExactly(
                instant("09:00"), instant("09:15"), instant("09:30"), instant("09:45"),
                instant("10:00"), instant("10:15"), instant("10:30"));
    }

    static List<Instant> startsOf(List<Interval> slots) {
        return slots.stream().map(Interval::start).toList();
    }

    static List<Instant> endsOf(List<Interval> slots) {
        return slots.stream().map(Interval::end).toList();
    }

    static Duration minutes(int value) {
        return Duration.ofMinutes(value);
    }

    static Instant instant(String hour) {
        return Instant.parse(DAY + hour + ":00Z");
    }

    static Interval interval(String from, String to) {
        return new Interval(instant(from), instant(to));
    }
}
