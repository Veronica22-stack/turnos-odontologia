package ar.turnosodontologia.consultorio.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifica que cada regla de {@link ModuleBoundaries} falle con su violacion
 * (nombrando origen y destino) y pase con la estructura valida.
 */
class ModuleBoundariesRulesTest {

    private static final String FIXTURES = "ar.turnosodontologia.consultorio.archfixture";

    private static String root(String fixture) {
        return FIXTURES + "." + fixture;
    }

    private static JavaClasses classesOf(String fixture) {
        return new ClassFileImporter().importPackages(root(fixture));
    }

    @Test
    void estructuraValidaCumpleTodasLasReglasDeDependencias() {
        String root = root("ok");
        JavaClasses classes = classesOf("ok");

        assertThatCode(() -> {
            ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes);
            ModuleBoundaries.allowedDependencyGraph(root).check(classes);
            ModuleBoundaries.noCycles(root).check(classes);
            ModuleBoundaries.domainIndependent(root).check(classes);
            ModuleBoundaries.applicationIndependent(root).check(classes);
        }).doesNotThrowAnyException();
    }

    @Test
    void accesoAInfraestructuraDeOtroModuloFallaNombrandoOrigenYDestino() {
        String root = root("violacion_infra");

        assertThatThrownBy(() -> ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classesOf("violacion_infra")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ReservarMal")
                .hasMessageContaining("AgendaRepositorio");
    }

    @Test
    void accesoAInfraestructuraAjenaNoViolaElGrafoPermitido() {
        // reservas -> agenda esta permitido en el grafo: solo la regla de api debe fallar.
        String root = root("violacion_infra");

        assertThatCode(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("violacion_infra")))
                .doesNotThrowAnyException();
    }

    @Test
    void dependenciaNoDeclaradaAgendaHaciaReservasFalla() {
        String root = root("violacion_grafo");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("violacion_grafo")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AgendaMal")
                .hasMessageContaining("ReservasApi");
    }

    @Test
    void dependenciaNoDeclaradaPasaLaReglaDeAccesoPorApi() {
        // El destino es una api: solo el grafo debe rechazarla.
        String root = root("violacion_grafo");

        assertThatCode(() -> ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classesOf("violacion_grafo")))
                .doesNotThrowAnyException();
    }

    @Test
    void auditoriaNoPuedeDependerDeModulosDeNegocio() {
        String root = root("violacion_auditoria");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("violacion_auditoria")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuditarMal")
                .hasMessageContaining("PacientesApi");
    }

    @Test
    void auditoriaAisladaDetectaDependenciasDirectasEnLasCuatroCapas() {
        String root = root("violacion_auditoria");

        assertThatThrownBy(() -> ModuleBoundaries.auditoriaIsolatedFromBusinessModules(root).check(classesOf("violacion_auditoria")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuditoriaApiMal")
                .hasMessageContaining("AgendaApi")
                .hasMessageContaining("AuditarMal")
                .hasMessageContaining("PacientesApi")
                .hasMessageContaining("AuditoriaDominioMal")
                .hasMessageContaining("HistoriaClinicaApi")
                .hasMessageContaining("AuditoriaInfraMal")
                .hasMessageContaining("ReservasApi");
    }

    @Test
    void auditoriaAisladaDetectaDependenciaTransitivaViaShared() {
        String root = root("violacion_auditoria_transitiva");

        assertThatThrownBy(() -> ModuleBoundaries.auditoriaIsolatedFromBusinessModules(root)
                .check(classesOf("violacion_auditoria_transitiva")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuditoriaTransitiva")
                .hasMessageContaining("ReservasApi");
    }

    @Test
    void auditoriaAisladaPasaConFixturesQueSoloUsanSharedYSuPropioModulo() {
        String root = root("ok_auditoria");

        assertThatCode(() -> ModuleBoundaries.auditoriaIsolatedFromBusinessModules(root).check(classesOf("ok_auditoria")))
                .doesNotThrowAnyException();
    }

    @Test
    void eventosDeAuditoriaSoloPorApiPasanConReporteDesdeHistoriaClinica() {
        String okAuditoria = root("ok_auditoria");
        String ok = root("ok");

        assertThatCode(() -> {
            ModuleBoundaries.auditEventsExposedOnlyThroughApi(okAuditoria).check(classesOf("ok_auditoria"));
            ModuleBoundaries.auditEventsExposedOnlyThroughApi(ok).check(classesOf("ok"));
            ModuleBoundaries.auditoriaIsolatedFromBusinessModules(ok).check(classesOf("ok"));
        }).doesNotThrowAnyException();
    }

    @Test
    void eventosDeAuditoriaSoloPorApiFallaConUnAdaptadorUsadoDesdeOtroModulo() {
        String root = root("violacion_auditoria");

        assertThatThrownBy(() -> ModuleBoundaries.auditEventsExposedOnlyThroughApi(root).check(classesOf("violacion_auditoria")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ReporteMal")
                .hasMessageContaining("AuditoriaInfraMal");
    }

    @Test
    void cicloEntreModulosFalla() {
        String root = root("violacion_ciclo");

        assertThatThrownBy(() -> ModuleBoundaries.noCycles(root).check(classesOf("violacion_ciclo")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("agenda")
                .hasMessageContaining("pacientes");
    }

    @Test
    void sinCiclosLaReglaPasa() {
        String root = root("ok");

        assertThatCode(() -> ModuleBoundaries.noCycles(root).check(classesOf("ok"))).doesNotThrowAnyException();
    }

    @Test
    void dominioQueUsaInfraestructuraFalla() {
        String root = root("violacion_dominio");

        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(root).check(classesOf("violacion_dominio")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteMal")
                .hasMessageContaining("PacienteRepositorio");
    }

    @Test
    void dominioQueUsaSpringWebFalla() {
        String root = root("violacion_dominio_web");

        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(root).check(classesOf("violacion_dominio_web")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteWeb")
                .hasMessageContaining("UriComponentsBuilder");
    }

    @Test
    void dominioQueUsaSpringStereotypeFallaNombrandoOrigenYDestino() {
        String root = root("violacion_dominio_spring");

        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(root).check(classesOf("violacion_dominio_spring")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteSpring")
                .hasMessageContaining("org.springframework.stereotype.Component");
    }

    @Test
    void dominioConAnotacionesJpaEHibernateEsValido() {
        String root = root("ok_dominio_jpa");

        assertThatCode(() -> ModuleBoundaries.domainIndependent(root).check(classesOf("ok_dominio_jpa")))
                .doesNotThrowAnyException();
    }

    @Test
    void dominioQueUsaJdbcFalla() {
        String root = root("violacion_dominio_sql");

        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(root).check(classesOf("violacion_dominio_sql")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteSql")
                .hasMessageContaining("java.sql.Connection");
    }

    @Test
    void aplicacionQueUsaLaApiDeSuPropioModuloFalla() {
        String root = root("violacion_aplicacion");

        assertThatThrownBy(() -> ModuleBoundaries.applicationIndependent(root).check(classesOf("violacion_aplicacion")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RegistrarPacienteMal")
                .hasMessageContaining("PacienteDto");
    }

    @Test
    void aplicacionQueUsaLaApiDeOtroModuloEsValida() {
        String root = root("ok");

        assertThatCode(() -> ModuleBoundaries.applicationIndependent(root).check(classesOf("ok")))
                .doesNotThrowAnyException();
    }

    @Test
    void estructuraCompletaPasaCuandoExisteCadaCombinacionModuloCapa() {
        String root = root("estructura_ok");

        assertThatCode(() -> ModuleBoundaries
                .completeStructure(root, List.of("agenda"), List.of("api", "application"))
                .check(classesOf("estructura_ok")))
                .doesNotThrowAnyException();
    }

    @Test
    void estructuraIncompletaNombraLaCapaFaltante() {
        String root = root("estructura_incompleta");

        assertThatThrownBy(() -> ModuleBoundaries
                .completeStructure(root, List.of("agenda"), List.of("api", "application"))
                .check(classesOf("estructura_incompleta")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("agenda.application");
    }

    @Test
    void paqueteDeModuloNoDeclaradoFalla() {
        String root = root("violacion_modulo_extra");

        assertThatThrownBy(() -> ModuleBoundaries
                .completeStructure(root, List.of("agenda"), List.of("api"))
                .check(classesOf("violacion_modulo_extra")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("inventario");
    }

    @Test
    void authRegistraAuditoriaEsUnaAristaPermitida() {
        String root = root("ok_auth");

        assertThatCode(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("ok_auth")))
                .doesNotThrowAnyException();
    }

    @Test
    void authNoPuedeDependerDeUnModuloDeNegocio() {
        String root = root("violacion_auth");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("violacion_auth")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuthApiMal")
                .hasMessageContaining("PacientesApi");
    }

    @Test
    void authDetectaLaDependenciaTransitivaPeroNombraLaAristaProhibida() {
        String root = root("violacion_auth_transitiva");
        JavaClasses classes = classesOf("violacion_auth_transitiva");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classes))
                .as("la arista prohibida es auditoria -> pacientes, no auth -> auditoria")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuditoriaPuente")
                .hasMessageContaining("PacientesApi");
        assertThatThrownBy(() -> ModuleBoundaries.authIsolatedFromBusinessModules(root).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AuditoriaPuente")
                .hasMessageContaining("PacientesApi")
                .hasMessageContaining("modulo pacientes");
    }

    @Test
    void authPuedeUsarSharedApiSinCrearAristasDeModulo() {
        String root = root("ok_auth");
        JavaClasses classes = classesOf("ok_auth");

        assertThatCode(() -> {
            ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes);
            ModuleBoundaries.allowedDependencyGraph(root).check(classes);
            ModuleBoundaries.authIsolatedFromBusinessModules(root).check(classes);
            ModuleBoundaries.securityConfinedToAuthInfrastructure(root).check(classes);
        }).doesNotThrowAnyException();
    }

    // ---- agenda -> pacientes (C-11, D4) ----

    @Test
    void agendaPuedeUsarLaApiDePacientes() {
        String root = root("ok_agenda_pacientes");
        JavaClasses classes = classesOf("ok_agenda_pacientes");

        assertThatCode(() -> {
            ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes);
            ModuleBoundaries.allowedDependencyGraph(root).check(classes);
            ModuleBoundaries.noCycles(root).check(classes);
        }).doesNotThrowAnyException();
    }

    @Test
    void agendaNoPuedeUsarApplicationDomainNiInfrastructureDePacientes() {
        String root = root("violacion_agenda_pacientes");
        JavaClasses classes = classesOf("violacion_agenda_pacientes");

        assertThatThrownBy(() -> ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AgendaUsaPacienteCasoDeUso")
                .hasMessageContaining("PacienteCasoDeUso")
                .hasMessageContaining("AgendaUsaPacienteDominio")
                .hasMessageContaining("PacienteDominio")
                .hasMessageContaining("AgendaUsaPacienteRepositorio")
                .hasMessageContaining("PacienteRepositorio");
        // El grafo si permite agenda -> pacientes: solo falla la regla de acceso por api.
        assertThatCode(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classes)).doesNotThrowAnyException();
    }

    @Test
    void pacientesNoPuedeUsarLaApiDeAgenda() {
        String root = root("violacion_pacientes_agenda");
        JavaClasses classes = classesOf("violacion_pacientes_agenda");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacientesUsaAgenda")
                .hasMessageContaining("AgendaApi");
    }

    @Test
    void agendaSigueSinPoderUsarReservas() {
        String root = root("violacion_grafo");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("violacion_grafo")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AgendaMal")
                .hasMessageContaining("ReservasApi");
    }

    @Test
    void reservasPuedeUsarLaOperacionDeReprogramacionDeLaApiDeAgenda() {
        String root = root("ok_reservas_agenda_api");
        JavaClasses classes = classesOf("ok_reservas_agenda_api");

        assertThatCode(() -> {
            ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes);
            ModuleBoundaries.allowedDependencyGraph(root).check(classes);
        }).doesNotThrowAnyException();
    }

    @Test
    void reservasNoPuedeUsarElServicioDeAplicacionDeTurnosDeAgenda() {
        String root = root("violacion_reservas_agenda_application");

        assertThatThrownBy(() -> ModuleBoundaries.crossModuleAccessOnlyThroughApi(root)
                .check(classesOf("violacion_reservas_agenda_application")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ReservasCreaTurno")
                .hasMessageContaining("AppointmentService");
    }

    // ---- notificaciones -> agenda.api, pacientes.api (C-13, D2) ----

    @Test
    void notificacionesPuedeUsarLaApiDeAgendaYPacientes() {
        String root = root("ok_notif");
        JavaClasses classes = classesOf("ok_notif");

        assertThatCode(() -> {
            ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes);
            ModuleBoundaries.allowedDependencyGraph(root).check(classes);
            ModuleBoundaries.noCycles(root).check(classes);
        }).doesNotThrowAnyException();
    }

    @Test
    void notificacionesNoPuedeUsarDomainNiInfrastructureDeAgendaNiPacientes() {
        String root = root("viol_notif_internas");
        JavaClasses classes = classesOf("viol_notif_internas");

        assertThatThrownBy(() -> ModuleBoundaries.crossModuleAccessOnlyThroughApi(root).check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("NotifUsaAgendaDominio")
                .hasMessageContaining("AgendaDominio")
                .hasMessageContaining("NotifUsaAgendaInfra")
                .hasMessageContaining("AgendaRepositorio")
                .hasMessageContaining("NotifUsaPacienteDominio")
                .hasMessageContaining("PacienteDominio")
                .hasMessageContaining("NotifUsaPacienteInfra")
                .hasMessageContaining("PacienteRepositorio");
        // El grafo si permite notificaciones -> agenda/pacientes: solo falla la regla de acceso por api.
        assertThatCode(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classes)).doesNotThrowAnyException();
    }

    @Test
    void agendaNoPuedeUsarLaApiDeNotificaciones() {
        String root = root("viol_agenda_notif");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("viol_agenda_notif")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AgendaUsaNotificaciones")
                .hasMessageContaining("NotificacionesApi");
    }

    @Test
    void pacientesNoPuedeUsarLaApiDeNotificaciones() {
        String root = root("viol_pac_notif");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("viol_pac_notif")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacientesUsaNotificaciones")
                .hasMessageContaining("NotificacionesApi");
    }

    @Test
    void notificacionesNoPuedeDependerDeAuth() {
        String root = root("viol_notif_auth");

        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(root).check(classesOf("viol_notif_auth")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("NotifUsaAuth")
                .hasMessageContaining("AuthApi");
    }
}
