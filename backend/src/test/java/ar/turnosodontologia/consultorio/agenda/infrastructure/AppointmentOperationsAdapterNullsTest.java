package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El puerto publico no debe terminar en NullPointerException (500) ante argumentos ausentes. */
@ExtendWith(MockitoExtension.class)
class AppointmentOperationsAdapterNullsTest {

    @Mock
    private AppointmentService service;

    @Test
    void reprogramarSinNuevoInicioEsErrorDeValidacionSobreNewStartsAt() {
        AppointmentOperations operations = new AppointmentOperationsAdapter(service);

        assertThatThrownBy(() -> operations.reschedule(UUID.randomUUID(), null, false))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("newStartsAt"));
        verifyNoInteractions(service);
    }

    @Test
    void reprogramarSinIdDeTurnoEsErrorDeValidacionSobreAppointmentId() {
        AppointmentOperations operations = new AppointmentOperationsAdapter(service);

        assertThatThrownBy(() -> operations.reschedule(null, Instant.parse("2026-12-10T18:00:00Z"), false))
                .isInstanceOfSatisfying(ValidationException.class,
                        error -> assertThat(error.field()).isEqualTo("appointmentId"));
        verifyNoInteractions(service);
    }
}
