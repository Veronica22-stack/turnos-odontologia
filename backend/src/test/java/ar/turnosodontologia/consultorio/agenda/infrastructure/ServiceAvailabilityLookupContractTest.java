package ar.turnosodontologia.consultorio.agenda.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.agenda.api.PublicService;
import ar.turnosodontologia.consultorio.agenda.api.ServiceAvailabilityLookup;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeService;
import ar.turnosodontologia.consultorio.agenda.domain.PracticeServiceRepository;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceAvailabilityLookupContractTest {

    @Mock
    private PracticeServiceRepository repository;

    @Mock
    private InstallationPractice installationPractice;

    private final UUID practiceId = UUID.randomUUID();

    @Test
    void elPuertoExponeSoloTiposDelJdkYSuApi() {
        assertThat(ServiceAvailabilityLookup.class.isInterface()).isTrue();
        for (Method method : ServiceAvailabilityLookup.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getPackageName()).doesNotContain("application", "jakarta.persistence");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertThat(parameter.getPackageName()).doesNotContain("application", "jakarta.persistence");
            }
        }
        assertThat(PublicService.class.getDeclaredFields()).extracting("name")
                .containsExactly("id", "durationMin", "active", "publicVisible");
    }

    @Test
    void prestacionPropiaSeConvierteEnRecordSinDatosDeLaEntidad() {
        UUID serviceId = UUID.randomUUID();
        when(installationPractice.id()).thenReturn(practiceId);
        when(repository.findById(serviceId)).thenReturn(Optional.of(servicio(serviceId, true, true, 30)));

        Optional<PublicService> resultado = adapter().findPublicService(practiceId, serviceId);

        assertThat(resultado).contains(new PublicService(serviceId, Duration.ofMinutes(30), true, true));
    }

    @Test
    void prestacionAjenaOInexistenteNoSeExpone() {
        UUID serviceId = UUID.randomUUID();
        when(installationPractice.id()).thenReturn(practiceId);
        when(repository.findById(serviceId)).thenReturn(Optional.of(PracticeService.existing(
                serviceId, UUID.randomUUID(), "Limpieza", null, 30, true, true, null, null)));

        assertThat(adapter().findPublicService(practiceId, serviceId)).isEmpty();
    }

    @Test
    void prestacionInactivaSeDevuelveConSuEstadoParaDistinguirElError() {
        UUID serviceId = UUID.randomUUID();
        when(installationPractice.id()).thenReturn(practiceId);
        when(repository.findById(serviceId)).thenReturn(Optional.of(servicio(serviceId, false, true, 30)));

        assertThat(adapter().findPublicService(practiceId, serviceId))
                .contains(new PublicService(serviceId, Duration.ofMinutes(30), false, true));
    }

    @Test
    void hasPublicServicesReflejaElCatalogoPublicable() {
        when(installationPractice.id()).thenReturn(practiceId);
        when(repository.findPublicByPracticeId(practiceId)).thenReturn(List.of(
                servicio(UUID.randomUUID(), true, true, 30)));

        assertThat(adapter().hasPublicServices(practiceId)).isTrue();
    }

    @Test
    void catalogoSinPrestacionesPublicablesDevuelveFalse() {
        when(installationPractice.id()).thenReturn(practiceId);
        when(repository.findPublicByPracticeId(practiceId)).thenReturn(List.of());

        assertThat(adapter().hasPublicServices(practiceId)).isFalse();
    }

    private ServiceAvailabilityLookupAdapter adapter() {
        return new ServiceAvailabilityLookupAdapter(repository, installationPractice);
    }

    private PracticeService servicio(UUID id, boolean active, boolean publicVisible, int durationMin) {
        return PracticeService.existing(id, practiceId, "Limpieza", null, durationMin,
                active, publicVisible, null, null);
    }
}
