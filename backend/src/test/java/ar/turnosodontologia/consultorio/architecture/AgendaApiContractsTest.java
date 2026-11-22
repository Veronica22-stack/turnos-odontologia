package ar.turnosodontologia.consultorio.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.api.BlockedInterval;
import ar.turnosodontologia.consultorio.agenda.api.BlockedRanges;
import ar.turnosodontologia.consultorio.agenda.api.ScheduleBlockRequest;
import ar.turnosodontologia.consultorio.agenda.api.ScheduleBlockResponse;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifica que los contratos nuevos de C-09 no filtran persistencia ni infraestructura. */
class AgendaApiContractsTest {

    private static final String API_PACKAGE = "ar.turnosodontologia.consultorio.agenda.api";

    @Test
    void laApiDeAgendaNoDependeDePersistenciaNiDeInfraestructura() {
        JavaClasses classes = new ClassFileImporter().importPackages(API_PACKAGE);

        assertThatCode(() -> ArchRuleDefinition.noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("jakarta.persistence..", "org.springframework.data..",
                        "ar.turnosodontologia.consultorio.agenda.infrastructure..")
                .allowEmptyShould(true)
                .check(classes)).doesNotThrowAnyException();
    }

    @Test
    void losContratosDeBloqueosUsanSoloTiposDelJdkODelApi() throws NoSuchMethodException {
        assertThat(parameterTypes(BlockedRanges.class.getMethod(
                "findOverlapping", java.util.UUID.class, java.time.Instant.class, java.time.Instant.class)))
                .allSatisfy(AgendaApiContractsTest::assertPublicType);
        assertThat(parameterTypes(AffectedAppointments.class.getMethod(
                "findWithin", java.util.UUID.class, java.time.Instant.class, java.time.Instant.class)))
                .allSatisfy(AgendaApiContractsTest::assertPublicType);
        assertThat(recordTypes(BlockedInterval.class)).allSatisfy(AgendaApiContractsTest::assertPublicType);
        assertThat(recordTypes(AffectedAppointment.class)).allSatisfy(AgendaApiContractsTest::assertPublicType);
    }

    @Test
    void elRequestNoPermiteEditarMarcasDeAuditoriaYLaRespuestaNoExponeJpa() {
        assertThat(ScheduleBlockRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("startsAt", "endsAt", "reason");
        assertThat(ScheduleBlockResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("id", "startsAt", "endsAt", "reason", "createdAt", "updatedAt",
                        "affectedAppointments");
        assertThat(recordTypes(ScheduleBlockResponse.class)).allSatisfy(AgendaApiContractsTest::assertPublicType);
    }

    private static List<Class<?>> parameterTypes(Method method) {
        List<Class<?>> types = new java.util.ArrayList<>(List.of(method.getReturnType()));
        types.addAll(List.of(method.getParameterTypes()));
        return types;
    }

    private static List<Class<?>> recordTypes(Class<?> type) {
        return java.util.Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getType)
                .toList();
    }

    private static void assertPublicType(Class<?> type) {
        assertThat(type.getPackageName())
                .as("tipo expuesto por la api de agenda")
                .satisfiesAnyOf(
                        name -> assertThat(name).startsWith("java."),
                        name -> assertThat(name).isEqualTo(API_PACKAGE),
                        name -> assertThat(name).startsWith("ar.turnosodontologia.consultorio.agenda.domain"));
    }
}
