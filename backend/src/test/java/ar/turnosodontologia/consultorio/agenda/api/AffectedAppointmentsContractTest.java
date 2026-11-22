package ar.turnosodontologia.consultorio.agenda.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AffectedAppointmentsContractTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID APPOINTMENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");

    @Test
    void contratoUsaSoloUuidEInstantes() throws NoSuchMethodException {
        Method method = AffectedAppointments.class.getMethod("findWithin", UUID.class, Instant.class, Instant.class);

        assertThat(method.getReturnType()).isEqualTo(List.class);
        assertThat(AffectedAppointment.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("appointmentId", "startsAt", "endsAt");
        assertThat(AffectedAppointment.class.getRecordComponents())
                .allSatisfy(component -> assertThat(component.getType()).isIn(UUID.class, Instant.class));
    }

    @Test
    void sinProveedorDeTurnosDevuelveListaVacia() {
        Optional<AffectedAppointments> provider = Optional.empty();

        assertThat(provider.map(p -> p.findWithin(PRACTICE_ID, NINE, ELEVEN)).orElseGet(List::of)).isEmpty();
    }

    @Test
    void proveedorSimuladoDevuelveTurnosSinModificarlos() {
        AffectedAppointment affected = new AffectedAppointment(APPOINTMENT_ID, NINE, ELEVEN);
        AffectedAppointments provider = (practiceId, from, to) -> List.of(affected);

        List<AffectedAppointment> result = provider.findWithin(PRACTICE_ID, NINE, ELEVEN);

        assertThat(result).containsExactly(affected);
        assertThat(result.get(0).appointmentId()).isEqualTo(APPOINTMENT_ID);
        assertThat(result.get(0).startsAt()).isEqualTo(NINE);
        assertThat(result.get(0).endsAt()).isEqualTo(ELEVEN);
    }
}
