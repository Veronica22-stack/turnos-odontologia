package ar.turnosodontologia.consultorio.historiaclinica.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationLookup;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentNotificationView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvolutionAppointmentGuardTest {

    private static final Instant NOW = Instant.parse("2026-06-10T15:30:00Z");

    private final UUID patient = UUID.randomUUID();
    private final Map<UUID, AppointmentNotificationView> appointments = new HashMap<>();
    private final AppointmentNotificationLookup lookup = id -> Optional.ofNullable(appointments.get(id));
    private final EvolutionAppointmentGuard guard =
            new EvolutionAppointmentGuard(lookup, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aceptaUnTurnoCompletadoDelMismoPaciente() {
        UUID id = appointment(patient, NOW.minusSeconds(86_400), AppointmentStatusView.COMPLETED);

        assertThatCode(() -> guard.check(patient, id)).doesNotThrowAnyException();
    }

    @Test
    void aceptaUnTurnoConfirmadoQueYaComenzoOComienzaAhora() {
        UUID started = appointment(patient, NOW.minusSeconds(60), AppointmentStatusView.CONFIRMED);
        UUID startsNow = appointment(patient, NOW, AppointmentStatusView.CONFIRMED);

        assertThatCode(() -> guard.check(patient, started)).doesNotThrowAnyException();
        assertThatCode(() -> guard.check(patient, startsNow)).doesNotThrowAnyException();
    }

    @Test
    void rechazaUnTurnoQueTodaviaNoComenzo() {
        UUID future = appointment(patient, NOW.plusSeconds(1), AppointmentStatusView.CONFIRMED);

        assertThatThrownBy(() -> guard.check(patient, future))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("EVOLUTION_APPOINTMENT_NOT_STARTED");
    }

    @Test
    void rechazaUnTurnoCanceladoOQueElPacienteNoAsistio() {
        UUID cancelled = appointment(patient, NOW.minusSeconds(3600), AppointmentStatusView.CANCELLED);
        UUID noShow = appointment(patient, NOW.minusSeconds(3600), AppointmentStatusView.NO_SHOW);

        for (UUID id : new UUID[] {cancelled, noShow}) {
            assertThatThrownBy(() -> guard.check(patient, id))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("EVOLUTION_APPOINTMENT_NOT_ATTENDED");
        }
    }

    @Test
    void elEstadoNoAsistidoTienePrioridadSobreElTurnoFuturo() {
        UUID futureCancelled = appointment(patient, NOW.plusSeconds(3600), AppointmentStatusView.CANCELLED);

        assertThatThrownBy(() -> guard.check(patient, futureCancelled))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("EVOLUTION_APPOINTMENT_NOT_ATTENDED");
    }

    @Test
    void unTurnoInexistenteYUnoDeOtroPacienteDanElMismoError() {
        UUID foreign = appointment(UUID.randomUUID(), NOW.minusSeconds(3600), AppointmentStatusView.COMPLETED);

        for (UUID id : new UUID[] {UUID.randomUUID(), foreign}) {
            assertThatThrownBy(() -> guard.check(patient, id))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("EVOLUTION_APPOINTMENT_NOT_FOUND");
        }
    }

    private UUID appointment(UUID patientId, Instant startsAt, AppointmentStatusView status) {
        UUID id = UUID.randomUUID();
        appointments.put(id, new AppointmentNotificationView(
                id, patientId, "Limpieza", startsAt, startsAt.plusSeconds(1800), status));
        return id;
    }
}
