package ar.turnosodontologia.consultorio.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.agenda.api.AppointmentCancelled;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOriginView;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentRescheduled;
import ar.turnosodontologia.consultorio.agenda.api.CancelledByView;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.support.AbstractIntegrationTest;
import ar.turnosodontologia.consultorio.support.AppointmentFixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Los eventos de turno llegan a oyentes AFTER_COMMIT solo si la transaccion confirma (design D11, RN-NO-05). */
@Import(AppointmentEventsIT.RecordingListenerConfig.class)
class AppointmentEventsIT extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDate DAY = LocalDate.of(2099, 12, 10);

    /** Oyente de prueba: registra los eventos recibidos despues del commit. */
    static class RecordingListener {
        final List<Object> received = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(AppointmentConfirmed event) {
            received.add(event);
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(AppointmentCancelled event) {
            received.add(event);
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(AppointmentRescheduled event) {
            received.add(event);
        }
    }

    @TestConfiguration
    static class RecordingListenerConfig {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    private final AppointmentService service;
    private final RecordingListener listener;
    private final TransactionTemplate transactions;
    private UUID patient;
    private UUID serviceId;

    AppointmentEventsIT(AppointmentService service, RecordingListener listener, TransactionTemplate transactions) {
        this.service = service;
        this.listener = listener;
        this.transactions = transactions;
    }

    @BeforeEach
    void setUp() {
        AppointmentFixtures.cleanAll();
        listener.received.clear();
        patient = AppointmentFixtures.patient(true);
        serviceId = AppointmentFixtures.service(30, true, true);
        AppointmentFixtures.workingHours(DAY.getDayOfWeek().getValue(), "09:00", "17:00");
    }

    @AfterEach
    void tearDown() {
        AppointmentFixtures.cleanAll();
    }

    private static Instant at(String time) {
        return DAY.atTime(LocalTime.parse(time)).atZone(ZONE).toInstant();
    }

    private AppointmentResult book(String start) {
        return service.book(new BookAppointmentCommand(patient, serviceId, at(start), false, "nota privada"));
    }

    @Test
    void elAltaPublicaElEventoConfirmadoTrasElCommitSinNotasNiDatosPersonales() {
        AppointmentResult created = book("10:00");

        assertThat(listener.received).hasSize(1);
        AppointmentConfirmed event = (AppointmentConfirmed) listener.received.get(0);
        assertThat(event.appointmentId()).isEqualTo(created.id());
        assertThat(event.patientId()).isEqualTo(patient);
        assertThat(event.serviceId()).isEqualTo(serviceId);
        assertThat(event.startsAt()).isEqualTo(at("10:00"));
        assertThat(event.endsAt()).isEqualTo(at("10:30"));
        assertThat(event.origin()).isEqualTo(AppointmentOriginView.INTERNAL);
        assertThat(event.toString()).doesNotContain("nota privada");
    }

    @Test
    void cancelarYReprogramarPublicanSusEventos() {
        AppointmentResult toCancel = book("10:00");
        AppointmentResult toMove = book("11:00");
        listener.received.clear();

        service.cancel(toCancel.id());
        service.reschedule(toMove.id(), at("14:00"), false);

        assertThat(listener.received).hasSize(2);
        AppointmentCancelled cancelled = (AppointmentCancelled) listener.received.get(0);
        assertThat(cancelled.appointmentId()).isEqualTo(toCancel.id());
        assertThat(cancelled.cancelledBy()).isEqualTo(CancelledByView.DENTIST);
        AppointmentRescheduled moved = (AppointmentRescheduled) listener.received.get(1);
        assertThat(moved.appointmentId()).isEqualTo(toMove.id());
        assertThat(moved.previousStartsAt()).isEqualTo(at("11:00"));
        assertThat(moved.previousEndsAt()).isEqualTo(at("11:30"));
        assertThat(moved.startsAt()).isEqualTo(at("14:00"));
        assertThat(moved.endsAt()).isEqualTo(at("14:30"));
    }

    @Test
    void unRechazoNoPublicaNingunEvento() {
        book("10:00");
        listener.received.clear();

        assertThatThrownBy(() -> book("10:00")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> service.book(new BookAppointmentCommand(patient, serviceId, at("19:00"), false,
                null))).isInstanceOf(DomainException.class);

        assertThat(listener.received).isEmpty();
    }

    @Test
    void siLaTransaccionSeRevierteElOyenteNoRecibeElEvento() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            book("10:00");
            throw new IllegalStateException("rollback forzado");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(listener.received).isEmpty();
        assertThat(AppointmentFixtures.countAppointments()).isZero();
    }

    @Test
    void completarYMarcarAusenteNoPublicanEventos() {
        AppointmentFixtures.appointment(patient, serviceId, "2020-03-05T13:00:00Z", "2020-03-05T13:30:00Z",
                "CONFIRMED");
        UUID absent = AppointmentFixtures.appointment(patient, serviceId, "2020-03-05T14:00:00Z",
                "2020-03-05T14:30:00Z", "CONFIRMED");
        UUID done = AppointmentFixtures.appointment(patient, serviceId, "2020-03-05T15:00:00Z",
                "2020-03-05T15:30:00Z", "CONFIRMED");

        service.noShow(absent);
        service.complete(done);

        assertThat(listener.received).isEmpty();
    }
}
