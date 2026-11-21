package ar.turnosodontologia.consultorio.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Reglas de fronteras del monolito modular (KB 08, design.md D5), expresadas como funciones
 * que reciben el paquete raiz para poder aplicarlas tanto al codigo de produccion como a los
 * paquetes fixture de {@code ModuleBoundariesRulesTest}.
 */
public final class ModuleBoundaries {

    public static final List<String> MODULES = List.of(
            "agenda", "reservas", "pacientes", "historiaclinica", "notificaciones", "auth", "auditoria", "shared");

    public static final List<String> LAYERS = List.of("api", "application", "domain", "infrastructure");

    private static final String SHARED = "shared";
    private static final String BOOT_CLASS = "ConsultorioApplication";

    /** Dependencias permitidas entre modulos de negocio (KB 08). Los modulos ausentes no dependen de ninguno. */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            "reservas", Set.of("agenda", "pacientes", "notificaciones"),
            "historiaclinica", Set.of("pacientes", "auditoria"));

    private static final List<String> FORBIDDEN_DOMAIN_PACKAGES =
            List.of("org.springframework.web.", "org.springframework.jdbc.", "java.sql.");

    private ModuleBoundaries() {
    }

    /** Una regla verificable: lanza {@link AssertionError} describiendo cada violacion. */
    @FunctionalInterface
    public interface Rule {
        void check(JavaClasses classes);
    }

    private record Location(String module, String layer) {
    }

    /** Ubica un paquete dentro de {@code root.<modulo>.<capa>}; null si esta fuera de la raiz. */
    private static Location locate(String packageName, String root) {
        String prefix = root + ".";
        if (!packageName.startsWith(prefix)) {
            return null;
        }
        String[] parts = packageName.substring(prefix.length()).split("\\.");
        return new Location(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private static Rule dependencyRule(String root, String description, BiPredicate<Location, Dependency> violates) {
        ArchCondition<JavaClass> condition = new ArchCondition<>(description) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                Location from = locate(origin.getPackageName(), root);
                if (from == null) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    if (violates.test(from, dependency)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
        ArchRule rule = ArchRuleDefinition.classes().should(condition).allowEmptyShould(true).as(description);
        return rule::check;
    }

    /** Un modulo solo depende de la capa {@code api} de otro modulo (o de {@code shared}). */
    public static Rule crossModuleAccessOnlyThroughApi(String root) {
        return dependencyRule(root, "los modulos solo acceden a otros modulos por su capa api", (from, dependency) -> {
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            return to != null
                    && !to.module().equals(from.module())
                    && !to.module().equals(SHARED)
                    && !to.layer().equals("api");
        });
    }

    /** Solo se permiten las dependencias entre modulos declaradas en KB 08. */
    public static Rule allowedDependencyGraph(String root) {
        return dependencyRule(root, "las dependencias entre modulos respetan el grafo permitido", (from, dependency) -> {
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            return to != null
                    && !from.module().equals(SHARED)
                    && !to.module().equals(from.module())
                    && !to.module().equals(SHARED)
                    && !ALLOWED_DEPENDENCIES.getOrDefault(from.module(), Set.of()).contains(to.module());
        });
    }

    /** No hay ciclos entre modulos. */
    public static Rule noCycles(String root) {
        ArchRule rule = SlicesRuleDefinition.slices().matching(root + ".(*)..").should().beFreeOfCycles();
        return rule::check;
    }

    /** {@code domain} no depende de api/application/infrastructure ni de Spring Web o JDBC. */
    public static Rule domainIndependent(String root) {
        return dependencyRule(root, "domain es independiente de las demas capas y del framework", (from, dependency) -> {
            if (!from.layer().equals("domain")) {
                return false;
            }
            String targetPackage = dependency.getTargetClass().getPackageName() + ".";
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            if (to != null) {
                return Set.of("api", "application", "infrastructure").contains(to.layer());
            }
            return FORBIDDEN_DOMAIN_PACKAGES.stream().anyMatch(targetPackage::startsWith);
        });
    }

    /**
     * {@code application} no depende de la {@code api} ni de la {@code infrastructure} de su propio modulo.
     * (La api de OTROS modulos si es accesible: es el canal permitido entre modulos.)
     */
    public static Rule applicationIndependent(String root) {
        return dependencyRule(root, "application no depende de api ni infrastructure de su modulo", (from, dependency) -> {
            if (!from.layer().equals("application")) {
                return false;
            }
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            return to != null
                    && to.module().equals(from.module())
                    && Set.of("api", "infrastructure").contains(to.layer());
        });
    }

    /** Existe cada combinacion modulo/capa y no hay paquetes fuera de la lista (salvo la clase de arranque). */
    public static Rule completeStructure(String root, List<String> modules, List<String> layers) {
        return classes -> {
            Set<String> packages = new LinkedHashSet<>();
            List<String> problems = new ArrayList<>();
            for (JavaClass javaClass : classes) {
                String packageName = javaClass.getPackageName();
                packages.add(packageName);
                if (packageName.equals(root)) {
                    if (!javaClass.getName().startsWith(root + "." + BOOT_CLASS)) {
                        problems.add("Clase fuera de un modulo en la raiz: " + javaClass.getName());
                    }
                    continue;
                }
                Location location = locate(packageName, root);
                if (location == null) {
                    continue;
                }
                if (!modules.contains(location.module())) {
                    problems.add("Paquete de modulo no declarado: " + root + "." + location.module());
                } else if (!location.layer().isEmpty() && !layers.contains(location.layer())) {
                    problems.add("Capa no declarada: " + root + "." + location.module() + "." + location.layer());
                }
            }
            for (String module : modules) {
                for (String layer : layers) {
                    String expected = root + "." + module + "." + layer;
                    boolean present = packages.stream().anyMatch(p -> p.equals(expected) || p.startsWith(expected + "."));
                    if (!present) {
                        problems.add("Falta el paquete " + expected + " (" + module + "." + layer + ")");
                    }
                }
            }
            if (!problems.isEmpty()) {
                throw new AssertionError("Estructura de modulos invalida:\n" + String.join("\n", new LinkedHashSet<>(problems)));
            }
        };
    }
}
