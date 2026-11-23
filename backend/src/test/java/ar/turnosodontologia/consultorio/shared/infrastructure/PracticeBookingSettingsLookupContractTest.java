package ar.turnosodontologia.consultorio.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettings;
import ar.turnosodontologia.consultorio.shared.api.PracticeBookingSettingsLookup;
import ar.turnosodontologia.consultorio.shared.application.InstallationPractice;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsData;
import ar.turnosodontologia.consultorio.shared.application.PracticeSettingsService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PracticeBookingSettingsLookupContractTest {

    @Mock
    private PracticeSettingsService settingsService;

    @Mock
    private InstallationPractice installationPractice;

    private final UUID practiceId = UUID.randomUUID();

    @Test
    void elPuertoExponeSoloLosCuatroValoresDeReserva() {
        assertThat(PracticeBookingSettingsLookup.class.isInterface()).isTrue();
        for (Method method : PracticeBookingSettingsLookup.class.getDeclaredMethods()) {
            assertThat(method.getReturnType().getPackageName()).doesNotContain("application", "jakarta.persistence");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertThat(parameter.getPackageName()).doesNotContain("application", "jakarta.persistence");
            }
        }
        assertThat(PracticeBookingSettings.class.getDeclaredFields()).extracting("name")
                .containsExactly("timezone", "slotGranularityMin", "minNoticeMin", "maxAdvanceDays");
    }

    @Test
    void mapeaLosValoresVigentesSinExponerDatosDelConsultorio() {
        when(installationPractice.id()).thenReturn(practiceId);
        when(settingsService.getSettings()).thenReturn(data(15, 120, 60));

        PracticeBookingSettings settings = adapter().findBookingSettings(practiceId);

        assertThat(settings).isEqualTo(new PracticeBookingSettings(
                "America/Argentina/Buenos_Aires", 15, 120, 60));
    }

    @Test
    void traduceValoresPersonalizadosCadaVezQueSeConsulta() {
        when(installationPractice.id()).thenReturn(practiceId);
        when(settingsService.getSettings()).thenReturn(data(30, 45, 90));

        assertThat(adapter().findBookingSettings(practiceId)).isEqualTo(
                new PracticeBookingSettings("America/Argentina/Buenos_Aires", 30, 45, 90));
    }

    @Test
    void rechazaOtroConsultorio() {
        when(installationPractice.id()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> adapter().findBookingSettings(practiceId))
                .isInstanceOf(IllegalStateException.class);
    }

    private PracticeBookingSettingsLookupAdapter adapter() {
        return new PracticeBookingSettingsLookupAdapter(settingsService, installationPractice);
    }

    private static PracticeSettingsData data(int slotGranularityMin, int minNoticeMin, int maxAdvanceDays) {
        return new PracticeSettingsData(
                UUID.randomUUID(), "Consultorio", "America/Argentina/Buenos_Aires",
                "email@example.com", "555", "Direccion", slotGranularityMin, minNoticeMin, maxAdvanceDays, 24);
    }
}
