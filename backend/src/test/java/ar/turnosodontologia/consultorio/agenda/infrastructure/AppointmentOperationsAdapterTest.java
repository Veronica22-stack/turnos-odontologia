package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentStatusView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentResult;
import ar.turnosodontologia.consultorio.agenda.application.AppointmentService;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentOrigin;
import ar.turnosodontologia.consultorio.agenda.domain.AppointmentStatus;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentOperationsAdapterTest {

    private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final UUID PATIENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID SERVICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final Instant START = Instant.parse("2026-12-10T18:00:00Z");
    private static final Instant END = Instant.parse("2026-12-10T18:45:00Z");

    @Mock
    private AppointmentService service;

    private AppointmentOperations operations;

    @BeforeEach
    void setUp() {
        operations = new AppointmentOperationsAdapter(service);
    }

    @Test
    void reprogramarDelegaEnElServicioYDevuelveLaVistaEquivalente() {
        when(service.reschedule(ID, START, false)).thenReturn(new AppointmentResult(ID, PATIENT_ID, SERVICE_ID,
                START, END, 45, AppointmentStatus.CONFIRMED, AppointmentOrigin.PUBLIC, false, null, null,
                "nota privada", START, START));

        AppointmentView view = operations.reschedule(ID, START, false);

        assertThat(view.id()).isEqualTo(ID);
        assertThat(view.patientId()).isEqualTo(PATIENT_ID);
        assertThat(view.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(view.startsAt()).isEqualTo(START);
        assertThat(view.endsAt()).isEqualTo(END);
        assertThat(view.durationMin()).isEqualTo(45);
        assertThat(view.status()).isEqualTo(AppointmentStatusView.CONFIRMED);
        assertThat(view.origin()).isEqualTo(AppointmentOriginView.PUBLIC);
        assertThat(view.overbooking()).isFalse();
    }

    @Test
    void elSobreturnoSePasaAlServicio() {
        when(service.reschedule(ID, START, true)).thenReturn(new AppointmentResult(ID, PATIENT_ID, SERVICE_ID,
                START, END, 45, AppointmentStatus.CONFIRMED, AppointmentOrigin.INTERNAL, true, null, null, null,
                START, START));

        assertThat(operations.reschedule(ID, START, true).overbooking()).isTrue();
    }

    @Test
    void conflictoBloqueoYFueraDeHorarioSePropaganConElMismoCodigoDelServicio() {
        when(service.reschedule(ID, START, false))
                .thenThrow(new ConflictException("APPOINTMENT_SLOT_TAKEN"))
                .thenThrow(new ConflictException("APPOINTMENT_SLOT_BLOCKED"))
                .thenThrow(new BusinessRuleException("OUTSIDE_WORKING_HOURS"));

        assertThatThrownBy(() -> operations.reschedule(ID, START, false))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_TAKEN");
        assertThatThrownBy(() -> operations.reschedule(ID, START, false))
                .isInstanceOf(ConflictException.class).hasMessage("APPOINTMENT_SLOT_BLOCKED");
        assertThatThrownBy(() -> operations.reschedule(ID, START, false))
                .isInstanceOf(BusinessRuleException.class).hasMessage("OUTSIDE_WORKING_HOURS");
    }
}
