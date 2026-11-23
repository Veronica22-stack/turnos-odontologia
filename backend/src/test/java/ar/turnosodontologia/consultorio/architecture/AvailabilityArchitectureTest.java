package ar.turnosodontologia.consultorio.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Gateway del change C-12: congela el grafo de modulos y las reglas de capa antes de agregar el motor de
 * disponibilidad. Los negativos son fixtures sinteticos ya existentes: demuestran que la regla detecta la
 * dependencia prohibida (no una asercion tautologica).
 */
class AvailabilityArchitectureTest {

    private static final String ROOT = "ar.turnosodontologia.consultorio";
    private static final String FIXTURES = ROOT + ".archfixture";

    private static JavaClasses production() {
        return new ClassFileImporter()
                .withImportOption(location -> location.contains("/classes/java/main/"))
                .importPackages(ROOT);
    }

    private static JavaClasses fixture(String name) {
        return new ClassFileImporter().importPackages(FIXTURES + "." + name);
    }

    @Test
    void elGrafoDeProduccionPermiteReservasHaciaAgendaYProhibeAgendaHaciaReservas() {
        JavaClasses classes = production();

        assertThatCode(() -> ModuleBoundaries.allowedDependencyGraph(ROOT).check(classes))
                .doesNotThrowAnyException();

        // Negativo sintetico: agenda -> reservas.api esta prohibido y la regla lo nombra.
        assertThatThrownBy(() -> ModuleBoundaries.allowedDependencyGraph(FIXTURES + ".violacion_grafo")
                .check(fixture("violacion_grafo")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("AgendaMal")
                .hasMessageContaining("ReservasApi");
    }

    @Test
    void elDominioDeReservasNoDependeDeApiSpringNiJdbc() {
        JavaClasses classes = production();

        assertThatCode(() -> ModuleBoundaries.domainIndependent(ROOT).check(classes))
                .doesNotThrowAnyException();

        // Negativo sintetico: dominio -> infrastructure y dominio -> Spring se detectan.
        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(FIXTURES + ".violacion_dominio")
                .check(fixture("violacion_dominio")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteMal");
        assertThatThrownBy(() -> ModuleBoundaries.domainIndependent(FIXTURES + ".violacion_dominio_spring")
                .check(fixture("violacion_dominio_spring")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("PacienteSpring");
    }

    @Test
    void applicationNoPuedeDependerDeLaApiNiDeLaInfrastructureDeSuPropioModulo() {
        JavaClasses classes = production();

        assertThatCode(() -> ModuleBoundaries.applicationIndependent(ROOT).check(classes))
                .doesNotThrowAnyException();

        // Negativo sintetico: pacientes.application -> pacientes.api se detecta.
        assertThatThrownBy(() -> ModuleBoundaries.applicationIndependent(FIXTURES + ".violacion_aplicacion")
                .check(fixture("violacion_aplicacion")))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("RegistrarPacienteMal")
                .hasMessageContaining("PacienteDto");
    }
}
