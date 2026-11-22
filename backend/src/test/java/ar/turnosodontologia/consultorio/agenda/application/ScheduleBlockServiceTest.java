package ar.turnosodontologia.consultorio.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentSlot;
import ar.turnosodontologia.consultorio.agenda.domain.AffectedAppointmentsProvider;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlock;
import ar.turnosodontologia.consultorio.agenda.domain.ScheduleBlockRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleBlockServiceTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID ID = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private static final UUID OTHER_ID = UUID.fromString("01900000-0000-7000-8000-000000000004");
    private static final UUID APPOINTMENT_ID = UUID.fromString("01900000-0000-7000-8000-000000000003");
    private static final UUID UNKNOWN_ID = UUID.fromString("01900000-0000-7000-8000-000000000099");
    private static final Instant NINE = Instant.parse("2026-12-10T09:00:00Z");
    private static final Instant ELEVEN = Instant.parse("2026-12-10T11:00:00Z");
    private static final Instant TWELVE = Instant.parse("2026-12-10T12:00:00Z");
    private static final Instant CREATED = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-12-02T00:00:00Z");
    private static final Clock CREATED_CLOCK = Clock.fixed(CREATED, ZoneOffset.UTC);
    private static final Clock UPDATED_CLOCK = Clock.fixed(UPDATED, ZoneOffset.UTC);

    @Mock
    private ScheduleBlockRepository repository;

    @Mock
    private InstallationPractice practice;

    @Mock
    private AffectedAppointmentsProvider appointments;

    @BeforeEach
    void configurePractice() {
        lenient().when(practice.id()).thenReturn(PRACTICE_ID);
    }

    @Test
    void creaBloqueoValidoConMarcasGeneradasPorElServidor() {
        stubSave();

        ScheduleBlockResult result = service(CREATED_CLOCK, Optional.empty()).create(NINE, ELEVEN, "Vacaciones");

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(result.startsAt()).isEqualTo(NINE);
        assertThat(result.endsAt()).isEqualTo(ELEVEN);
        assertThat(result.reason()).isEqualTo("Vacaciones");
        assertThat(result.createdAt()).isEqualTo(CREATED);
        assertThat(result.updatedAt()).isEqualTo(CREATED);
        assertThat(result.affectedAppointments()).isEmpty();

        ArgumentCaptor<ScheduleBlock> captor = ArgumentCaptor.forClass(ScheduleBlock.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(CREATED);
        assertThat(captor.getValue().updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void listaSinFiltroOrdenadoPorInicio() {
        when(repository.findAllByPracticeIdOrderByStartsAtAsc(PRACTICE_ID))
                .thenReturn(List.of(block(OTHER_ID, PRACTICE_ID, ELEVEN, TWELVE),
                        block(ID, PRACTICE_ID, NINE, ELEVEN)));

        List<ScheduleBlockResult> result = service(CREATED_CLOCK, Optional.empty()).list(null, null);

        assertThat(result).extracting(ScheduleBlockResult::id).containsExactly(ID, OTHER_ID);
    }

    @Test
    void listaConFiltroUsaSoloBloqueosQueSolapan() {
        when(repository.findOverlapping(PRACTICE_ID, NINE, TWELVE))
                .thenReturn(List.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));

        List<ScheduleBlockResult> result = service(CREATED_CLOCK, Optional.empty()).list(NINE, TWELVE);

        assertThat(result).extracting(ScheduleBlockResult::id).containsExactly(ID);
        verify(repository).findOverlapping(PRACTICE_ID, NINE, TWELVE);
    }

    @Test
    void obtieneDetalleDelBloqueoDelConsultorio() {
        when(repository.findById(ID)).thenReturn(Optional.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));

        ScheduleBlockResult result = service(CREATED_CLOCK, Optional.empty()).get(ID);

        assertThat(result.id()).isEqualTo(ID);
        assertThat(result.reason()).isEqualTo("Vacaciones");
    }

    @Test
    void editaBloqueoActualizandoSoloUpdatedAt() {
        when(repository.findById(ID)).thenReturn(Optional.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));
        when(repository.findOverlapping(PRACTICE_ID, NINE, TWELVE))
                .thenReturn(List.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));
        stubSave();

        ScheduleBlockResult result = service(UPDATED_CLOCK, Optional.empty()).update(ID, NINE, TWELVE, "Nuevo motivo");

        assertThat(result.endsAt()).isEqualTo(TWELVE);
        assertThat(result.reason()).isEqualTo("Nuevo motivo");
        assertThat(result.createdAt()).isEqualTo(CREATED);
        assertThat(result.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void eliminaBloqueoDelConsultorio() {
        ScheduleBlock block = block(ID, PRACTICE_ID, NINE, ELEVEN);
        when(repository.findById(ID)).thenReturn(Optional.of(block));

        service(CREATED_CLOCK, Optional.empty()).delete(ID);

        verify(repository).delete(block);
    }

    @Test
    void detectaSolapamientoAntesDeGuardar() {
        when(repository.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T10:00:00Z"), TWELVE))
                .thenReturn(List.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));

        assertThatThrownBy(() -> service(CREATED_CLOCK, Optional.empty())
                .create(Instant.parse("2026-12-10T10:00:00Z"), TWELVE, "Vacaciones"))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("SCHEDULE_BLOCK_OVERLAP"));
        verify(repository, never()).save(any());
    }

    @Test
    void bloqueoInexistenteFallaEnDetalleYEliminacion() {
        when(repository.findById(UNKNOWN_ID)).thenReturn(Optional.empty());
        ScheduleBlockService service = service(CREATED_CLOCK, Optional.empty());

        assertThatThrownBy(() -> service.get(UNKNOWN_ID))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("SCHEDULE_BLOCK_NOT_FOUND"));
        assertThatThrownBy(() -> service.delete(UNKNOWN_ID))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("SCHEDULE_BLOCK_NOT_FOUND"));
    }

    @Test
    void sinProveedorDeTurnosDevuelveAfectadosVacios() {
        stubSave();

        ScheduleBlockResult result = service(CREATED_CLOCK, Optional.empty()).create(NINE, ELEVEN, "Trámite");

        assertThat(result.affectedAppointments()).isEmpty();
    }

    @Test
    void proveedorSimuladoAportaAfectadosSinModificarElBloqueo() {
        AffectedAppointmentSlot affected = new AffectedAppointmentSlot(APPOINTMENT_ID, NINE, TWELVE);
        when(appointments.findWithin(PRACTICE_ID, NINE, ELEVEN)).thenReturn(List.of(affected));
        stubSave();

        ScheduleBlockResult result = service(UPDATED_CLOCK, Optional.of(appointments))
                .create(NINE, ELEVEN, "Trámite");

        assertThat(result.affectedAppointments()).containsExactly(affected);
        assertThat(result.startsAt()).isEqualTo(NINE);
        assertThat(result.endsAt()).isEqualTo(ELEVEN);
        verify(appointments).findWithin(PRACTICE_ID, NINE, ELEVEN);
    }

    @Test
    void filtroConFromMayorOIgualAToSeRechaza() {
        ScheduleBlockService service = service(CREATED_CLOCK, Optional.empty());

        assertThatThrownBy(() -> service.list(ELEVEN, NINE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(NINE, NINE)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findAllByPracticeIdOrderByStartsAtAsc(any());
    }

    @Test
    void rangoInvertidoSeRechazaSinGuardar() {
        assertThatThrownBy(() -> service(CREATED_CLOCK, Optional.empty()).create(ELEVEN, NINE, "Vacaciones"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void edicionQueSolapaOtroBloqueoSeRechaza() {
        when(repository.findById(ID)).thenReturn(Optional.of(block(ID, PRACTICE_ID, NINE, ELEVEN)));
        when(repository.findOverlapping(PRACTICE_ID, Instant.parse("2026-12-10T10:00:00Z"), TWELVE))
                .thenReturn(List.of(block(OTHER_ID, PRACTICE_ID, NINE, ELEVEN)));

        assertThatThrownBy(() -> service(UPDATED_CLOCK, Optional.empty())
                .update(ID, Instant.parse("2026-12-10T10:00:00Z"), TWELVE, "Nuevo motivo"))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("SCHEDULE_BLOCK_OVERLAP"));
        verify(repository, never()).save(any());
    }

    @Test
    void filtroConUnSoloExtremoSeRechaza() {
        ScheduleBlockService service = service(CREATED_CLOCK, Optional.empty());

        assertThatThrownBy(() -> service.list(NINE, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.list(null, TWELVE)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).findOverlapping(any(), any(), any());
    }

    @Test
    void bloqueoDeOtroConsultorioNoEsVisibleEnDetalleNiEliminacion() {
        when(repository.findById(ID))
                .thenReturn(Optional.of(block(ID, UUID.randomUUID(), NINE, ELEVEN)));
        ScheduleBlockService service = service(CREATED_CLOCK, Optional.empty());

        assertThatThrownBy(() -> service.get(ID))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((DomainException) e).code()).isEqualTo("SCHEDULE_BLOCK_NOT_FOUND"));
        assertThatThrownBy(() -> service.delete(ID))
                .isInstanceOf(NotFoundException.class);
    }

    private ScheduleBlockService service(Clock clock, Optional<AffectedAppointmentsProvider> provider) {
        return new ScheduleBlockService(repository, practice, clock, provider);
    }

    private void stubSave() {
        when(repository.save(any(ScheduleBlock.class))).thenAnswer(invocation -> {
            ScheduleBlock block = invocation.getArgument(0);
            return ScheduleBlock.existing(ID, block.practiceId(), block.startsAt(), block.endsAt(),
                    block.reason(), block.createdAt(), block.updatedAt());
        });
    }

    private static ScheduleBlock block(UUID id, UUID practiceId, Instant startsAt, Instant endsAt) {
        return ScheduleBlock.existing(id, practiceId, startsAt, endsAt, "Vacaciones", CREATED, CREATED);
    }
}
