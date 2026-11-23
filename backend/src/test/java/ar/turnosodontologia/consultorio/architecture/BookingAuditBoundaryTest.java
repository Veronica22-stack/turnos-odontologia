package ar.turnosodontologia.consultorio.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** C-15: `reservas` solo puede alcanzar `auditoria.api`; la arista es de ida (D6). */
class BookingAuditBoundaryTest {

    private static final String ROOT = "ar.turnosodontologia.consultorio";

    private static JavaClasses production;

    @BeforeAll
    static void importProductionClasses() {
        production = new ClassFileImporter()
                .withImportOption(location -> location.contains("/classes/java/main/"))
                .importPackages(ROOT);
    }

    @Test
    void reservasSoloDependeDeAuditoriaApi() {
        classes().that().resideInAPackage(ROOT + ".reservas..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        ROOT + ".reservas..",
                        ROOT + ".auditoria.api..",
                        ROOT + ".agenda.api..",
                        ROOT + ".pacientes.api..",
                        ROOT + ".notificaciones.api..",
                        ROOT + ".shared..",
                        "java..", "jakarta..", "org.slf4j..", "org.hibernate..", "org.springframework..")
                .check(production);
    }

    @Test
    void auditoriaNoDependeDeReservas() {
        ArchCondition<JavaClass> condition = new ArchCondition<>("auditoria no depende de reservas") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    if (dependency.getTargetClass().getPackageName().startsWith(ROOT + ".reservas")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
        ArchRuleDefinition.classes().that().resideInAPackage(ROOT + ".auditoria..")
                .should(condition).allowEmptyShould(true).check(production);
    }
}
