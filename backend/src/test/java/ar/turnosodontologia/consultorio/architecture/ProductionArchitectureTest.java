package ar.turnosodontologia.consultorio.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Aplica las reglas de fronteras de modulos al codigo de produccion (falla el build ante una violacion). */
class ProductionArchitectureTest {

    private static final String ROOT = "ar.turnosodontologia.consultorio";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(location -> location.contains("/classes/java/main/"))
                .importPackages(ROOT);
    }

    @Test
    void existeCadaCombinacionModuloCapa() {
        ModuleBoundaries.completeStructure(ROOT, ModuleBoundaries.MODULES, ModuleBoundaries.LAYERS).check(production);
    }

    @Test
    void losModulosSoloSeAccedenPorSuApi() {
        ModuleBoundaries.crossModuleAccessOnlyThroughApi(ROOT).check(production);
    }

    @Test
    void lasDependenciasEntreModulosRespetanElGrafoPermitido() {
        ModuleBoundaries.allowedDependencyGraph(ROOT).check(production);
    }

    @Test
    void noHayCiclosEntreModulos() {
        ModuleBoundaries.noCycles(ROOT).check(production);
    }

    @Test
    void elDominioEsIndependienteDelFramework() {
        ModuleBoundaries.domainIndependent(ROOT).check(production);
    }

    @Test
    void laAplicacionNoDependeDeApiNiInfraestructuraPropias() {
        ModuleBoundaries.applicationIndependent(ROOT).check(production);
    }

    @Test
    void auditoriaEstaAisladaDeLosModulosDeNegocio() {
        ModuleBoundaries.auditoriaIsolatedFromBusinessModules(ROOT).check(production);
    }

    @Test
    void losEventosDeAuditoriaSoloSeExponenPorSuApi() {
        ModuleBoundaries.auditEventsExposedOnlyThroughApi(ROOT).check(production);
    }

    @Test
    void authEstaAisladoDeLosModulosDeNegocio() {
        ModuleBoundaries.authIsolatedFromBusinessModules(ROOT).check(production);
    }

    @Test
    void laSeguridadHttpEstaConfinadaEnAuthInfrastructure() {
        ModuleBoundaries.securityConfinedToAuthInfrastructure(ROOT).check(production);
    }
}
