package ar.turnosodontologia.consultorio.agenda.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ServiceCatalogServiceTest {

    private static final UUID PRACTICE_ID = UUID.fromString("01900000-0000-7000-8000-000000000001");

    @Mock
    private PracticeServiceRepository practiceServiceRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private InstallationPractice installationPractice;

    private ServiceCatalogService service;

    @BeforeEach
    void setUp() {
        service = new ServiceCatalogService(practiceServiceRepository, practiceRepository, installationPractice);
    }

    @Test
    void crearUsaLaPracticeDeLaInstalacionYLaGranularidadVigente() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(practiceRepository.findById(PRACTICE_ID)).thenReturn(Optional.of(practice(15)));
        when(practiceServiceRepository.save(any(PracticeService.class))).thenAnswer(inv -> inv.getArgument(0));

        ServiceData created = service.createService("Dolor dental", "Descripcion", 45, true);

        ArgumentCaptor<PracticeService> captor = ArgumentCaptor.forClass(PracticeService.class);
        verify(practiceServiceRepository).save(captor.capture());
        assertThat(captor.getValue().practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(captor.getValue().name()).isEqualTo("Dolor dental");
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(created.practiceId()).isEqualTo(PRACTICE_ID);
        assertThat(created.durationMin()).isEqualTo(45);
    }

    @Test
    void listaDevuelveActivasEInactivas() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        PracticeService activa = PracticeService.create(PRACTICE_ID, "A", null, 30, true);
        PracticeService inactiva = PracticeService.create(PRACTICE_ID, "B", null, 30, false);
        inactiva.deactivate();
        setId(activa);
        setId(inactiva);
        when(practiceServiceRepository.findAllByPracticeIdOrderByNameAsc(PRACTICE_ID)).thenReturn(List.of(activa, inactiva));

        List<ServiceData> list = service.listServices();

        assertThat(list).extracting(ServiceData::name).containsExactly("A", "B");
        assertThat(list.get(0).active()).isTrue();
        assertThat(list.get(1).active()).isFalse();
    }

    @Test
    void idInexistenteLanzaServiceNotFound() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(practiceServiceRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getService(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).code()).isEqualTo("SERVICE_NOT_FOUND"));
    }

    @Test
    void duracionNoMultiploEnAltaLanza422YNoGuarda() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(practiceRepository.findById(PRACTICE_ID)).thenReturn(Optional.of(practice(15)));

        assertThatThrownBy(() -> service.createService("Dolor", null, 20, false))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code())
                        .isEqualTo("DURATION_NOT_MULTIPLE_OF_GRANULARITY"));
        verify(practiceServiceRepository, never()).save(any(PracticeService.class));
    }

    @Test
    void laGranularidadSeLeeVigenteEnCadaLlamada() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        when(practiceRepository.findById(PRACTICE_ID)).thenReturn(Optional.of(practice(15)));
        when(practiceServiceRepository.save(any(PracticeService.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createService("Dolor", null, 30, false);

        when(practiceRepository.findById(PRACTICE_ID)).thenReturn(Optional.of(practice(20)));
        assertThatThrownBy(() -> service.createService("Otro", null, 30, false))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void duracionNoMultiploEnEdicionLanza422SinPersistir() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        PracticeService existing = PracticeService.create(PRACTICE_ID, "Dolor", null, 45, true);
        setId(existing);
        when(practiceServiceRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(practiceRepository.findById(PRACTICE_ID)).thenReturn(Optional.of(practice(15)));

        assertThatThrownBy(() -> service.updateService(existing.id(), "Dolor", null, 20, false, true))
                .isInstanceOf(BusinessRuleException.class);
        verify(practiceServiceRepository, never()).save(any(PracticeService.class));
    }

    @Test
    void deactivateRepetidoNoFalla() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        PracticeService existing = PracticeService.create(PRACTICE_ID, "Dolor", null, 45, true);
        setId(existing);
        when(practiceServiceRepository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(practiceServiceRepository.save(any(PracticeService.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deactivateService(existing.id());
        service.deactivateService(existing.id());

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void prestacionDeOtraPracticeLanza404() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        PracticeService other = PracticeService.create(UUID.randomUUID(), "A", null, 30, false);
        setId(other);
        when(practiceServiceRepository.findById(other.id())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.getService(other.id()))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).code()).isEqualTo("SERVICE_NOT_FOUND"));
    }

    @Test
    void elCatalogoPublicoExcluyeInactivasYNovalVisible() {
        when(installationPractice.id()).thenReturn(PRACTICE_ID);
        PracticeService publica = PracticeService.create(PRACTICE_ID, "A", null, 30, true);
        setId(publica);
        when(practiceServiceRepository.findPublicByPracticeId(PRACTICE_ID)).thenReturn(List.of(publica));

        List<PublicServiceData> list = service.listPublicServices();

        assertThat(list).extracting(PublicServiceData::name).containsExactly("A");
    }

    private static Practice practice(int granularity) {
        return Practice.existing(
                PRACTICE_ID, "Consultorio", "America/Argentina/Buenos_Aires", null, null, null,
                granularity, 120, 30, 24);
    }

    private static void setId(PracticeService service) {
        ReflectionTestUtils.setField(service, "id", UUID.randomUUID());
    }
}
