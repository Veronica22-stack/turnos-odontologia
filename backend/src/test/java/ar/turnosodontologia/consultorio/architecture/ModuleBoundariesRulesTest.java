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
}
