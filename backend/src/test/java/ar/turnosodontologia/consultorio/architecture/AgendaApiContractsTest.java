package ar.turnosodontologia.consultorio.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointment;
import ar.turnosodontologia.consultorio.agenda.api.AffectedAppointments;
import ar.turnosodontologia.consultorio.agenda.api.BlockedInterval;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentCancelled;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentConfirmed;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentOperations;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentRescheduled;
import ar.turnosodontologia.consultorio.agenda.api.AppointmentView;
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
    void losContratosDeOcupacionConservanSusFirmasParaElMotorDeDisponibilidad() throws NoSuchMethodException {
        Method blocked = BlockedRanges.class.getMethod(
                "findOverlapping", java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);
        Method appointments = AffectedAppointments.class.getMethod(
                "findWithin", java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);

        assertThat(blocked.getReturnType()).isEqualTo(List.class);
        assertThat(appointments.getReturnType()).isEqualTo(List.class);
        assertThat(blocked.getParameterTypes())
                .containsExactly(java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);
        assertThat(appointments.getParameterTypes())
                .containsExactly(java.util.UUID.class, java.time.Instant.class, java.time.Instant.class);
        assertThat(blockedIntervalTypes()).containsExactly(BlockedInterval.class, AffectedAppointment.class);
    }

    private static List<Class<?>> blockedIntervalTypes() {
        return List.of(BlockedInterval.class, AffectedAppointment.class);
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

    // ---- contratos de turnos (C-11, D11, D16) ----

    @Test
    void appointmentOperationsDeclaraAltaPublicaReprogramacionYCancelacionDelPaciente() {
        // C-11 exponía solo reschedule; C-14 agrega book y C-15 agrega cancel (ambos delegan en AppointmentService).
        assertThat(AppointmentOperations.class.getDeclaredMethods())
                .extracting(Method::getName)
                .containsExactlyInAnyOrder("book", "reschedule", "cancel");
        assertThat(java.util.Arrays.stream(AppointmentOperations.class.getDeclaredMethods())
                .flatMap(method -> parameterTypes(method).stream()))
                .allSatisfy(AgendaApiContractsTest::assertPublicType);
    }

    @Test
    void laVistaYLosEventosDeTurnoSoloContienenIdentificadoresInstantesYEnums() {
        List<Class<?>> contracts = List.of(AppointmentView.class, AppointmentConfirmed.class,
                AppointmentCancelled.class, AppointmentRescheduled.class);

        for (Class<?> contract : contracts) {
            assertThat(recordTypes(contract))
                    .as(contract.getSimpleName())
                    .allSatisfy(type -> assertThat(type.isEnum() || type == java.util.UUID.class
                            || type == java.time.Instant.class || type == boolean.class || type == int.class)
                            .as("tipo " + type.getName()).isTrue())
                    .allSatisfy(AgendaApiContractsTest::assertPublicType);
            assertThat(java.util.Arrays.stream(contract.getRecordComponents()).map(RecordComponent::getName))
                    .as("sin notas ni datos personales en " + contract.getSimpleName())
                    .doesNotContain("notes", "firstName", "lastName", "dni", "email", "phone");
        }
    }

    @Test
    void losEventosTienenLosComponentesDelDiseno() {
        assertThat(AppointmentConfirmed.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("appointmentId", "practiceId", "patientId", "serviceId", "startsAt", "endsAt",
                        "origin");
        assertThat(AppointmentCancelled.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("appointmentId", "practiceId", "patientId", "startsAt", "endsAt", "cancelledBy");
        assertThat(AppointmentRescheduled.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("appointmentId", "practiceId", "patientId", "previousStartsAt", "previousEndsAt",
                        "startsAt", "endsAt");
    }
}
