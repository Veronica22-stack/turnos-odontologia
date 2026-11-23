package ar.turnosodontologia.consultorio.reservas.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ar.turnosodontologia.consultorio.architecture.ModuleBoundaries;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityLookup;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityQuery;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityResult;
import ar.turnosodontologia.consultorio.reservas.api.AvailabilityStatus;
import ar.turnosodontologia.consultorio.reservas.api.AvailableSlot;
import ar.turnosodontologia.consultorio.reservas.application.AvailabilityOutcome;
import ar.turnosodontologia.consultorio.reservas.application.AvailabilityUseCase;
import ar.turnosodontologia.consultorio.reservas.domain.Audience;
import ar.turnosodontologia.consultorio.reservas.domain.Interval;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityApiContractTest {

    private static final String ROOT = "ar.turnosodontologia.consultorio";

    @Mock
    private AvailabilityUseCase useCase;

    @Test
    void elContratoPublicoNoExponePiiNiTiposDeFramework() {
        assertThat(AvailabilityLookup.class.isInterface()).isTrue();
        assertThat(AvailabilityQuery.class.getDeclaredFields()).extracting("name")
                .containsExactly("practiceId", "serviceId", "date", "audience");
        assertThat(AvailabilityResult.class.getDeclaredFields()).extracting("name")
                .containsExactly("status", "slots");
        assertThat(AvailableSlot.class.getDeclaredFields()).extracting("name")
                .containsExactly("startsAt", "endsAt");
        assertThat(AvailabilityStatus.values()).containsExactly(
                AvailabilityStatus.OK, AvailabilityStatus.NO_ACTIVE_SERVICES);
        assertThat(java.util.Arrays.stream(AvailabilityQuery.class.getDeclaredFields())
                .map(field -> field.getType().getName()).toList())
                .noneMatch(name -> name.contains("Http") || name.contains("Persistence"));
    }

    @Test
    void applicationNoImportaLaApiDelPropioModulo() {
        ModuleBoundaries.applicationIndependent(ROOT).check(new ClassFileImporter()
                .withImportOption(location -> location.contains("/classes/java/main/"))
                .importPackages(ROOT));
    }

    @Test
    void laFachadaMapeaElOutcomeALosRecordsDeLaApi() {
        UUID practiceId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Instant start = Instant.parse("2026-03-10T12:00:00Z");
        Instant end = start.plus(Duration.ofMinutes(30));
        AvailabilityQuery query = new AvailabilityQuery(
                practiceId, serviceId, LocalDate.of(2026, 3, 10), ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience.PUBLIC);
        when(useCase.findAvailability(new ar.turnosodontologia.consultorio.reservas.application.AvailabilityQuery(
                practiceId, serviceId, LocalDate.of(2026, 3, 10), Audience.PUBLIC)))
                .thenReturn(new AvailabilityOutcome(false, List.of(new Interval(start, end))));

        AvailabilityResult result = new AvailabilityLookupAdapter(useCase).availability(query);

        assertThat(result.status()).isEqualTo(AvailabilityStatus.OK);
        assertThat(result.slots()).containsExactly(new AvailableSlot(start, end));
    }

    @Test
    void laFachadaPropagaElEstadoSinServiciosActivos() {
        UUID practiceId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        AvailabilityQuery query = new AvailabilityQuery(
                practiceId, serviceId, LocalDate.of(2026, 3, 10), ar.turnosodontologia.consultorio.reservas.api.AvailabilityAudience.PUBLIC);
        when(useCase.findAvailability(new ar.turnosodontologia.consultorio.reservas.application.AvailabilityQuery(
                practiceId, serviceId, LocalDate.of(2026, 3, 10), Audience.PUBLIC)))
                .thenReturn(new AvailabilityOutcome(true, List.of()));

        AvailabilityResult result = new AvailabilityLookupAdapter(useCase).availability(query);

        assertThat(result.status()).isEqualTo(AvailabilityStatus.NO_ACTIVE_SERVICES);
        assertThat(result.slots()).isEmpty();
    }
}
