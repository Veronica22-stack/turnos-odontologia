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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
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

    public static final String AGENDA = "agenda";
    public static final String RESERVAS = "reservas";
    public static final String PACIENTES = "pacientes";
    public static final String HISTORIA_CLINICA = "historiaclinica";
    public static final String NOTIFICACIONES = "notificaciones";
    public static final String AUTH = "auth";
    public static final String AUDITORIA = "auditoria";
    public static final String SHARED = "shared";

    private static final String API = "api";
    private static final String APPLICATION = "application";
    private static final String DOMAIN = "domain";
    private static final String INFRASTRUCTURE = "infrastructure";

    public static final List<String> MODULES = List.of(
            AGENDA, RESERVAS, PACIENTES, HISTORIA_CLINICA, NOTIFICACIONES, AUTH, AUDITORIA, SHARED);

    public static final List<String> LAYERS = List.of(API, APPLICATION, DOMAIN, INFRASTRUCTURE);

    private static final String BOOT_CLASS = "ConsultorioApplication";

    /** Dependencias permitidas entre modulos de negocio (KB 08). Los modulos ausentes no dependen de ninguno. */
    private static final Map<String, Set<String>> ALLOWED_DEPENDENCIES = Map.of(
            RESERVAS, Set.of(AGENDA, PACIENTES, NOTIFICACIONES),
            HISTORIA_CLINICA, Set.of(PACIENTES, AUDITORIA),
            AUTH, Set.of(AUDITORIA));

    private static final List<String> FORBIDDEN_DOMAIN_PACKAGES =
            List.of("org.springframework.", "java.sql.");

    /** Modulos de negocio que ni auth ni auditoria pueden alcanzar, directa ni transitivamente (design D11). */
    private static final Set<String> BUSINESS_MODULES =
            Set.of(AGENDA, RESERVAS, PACIENTES, HISTORIA_CLINICA, NOTIFICACIONES);

    /** Modulos que auditoria no puede alcanzar, ni directa ni transitivamente (design D5). */
    private static final Set<String> FORBIDDEN_AUDITORIA_MODULES = Set.of(
            AGENDA, RESERVAS, PACIENTES, HISTORIA_CLINICA, NOTIFICACIONES, AUTH);

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
                    && !to.layer().equals(API);
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

    /**
     * Ninguna clase de auditoria alcanza un modulo de negocio, ni directa ni transitivamente. El mensaje nombra la
     * clase de origen y la de destino (con el camino intermedio cuando la dependencia es indirecta).
     */
    public static Rule auditoriaIsolatedFromBusinessModules(String root) {
        return isolatedFromModules(root, AUDITORIA, FORBIDDEN_AUDITORIA_MODULES,
                "auditoria aislada de los modulos de negocio");
    }

    /** auth puede usar auditoria.api y shared, pero nunca alcanzar un modulo de negocio (design D11). */
    public static Rule authIsolatedFromBusinessModules(String root) {
        return isolatedFromModules(root, AUTH, BUSINESS_MODULES, "auth aislado de los modulos de negocio");
    }

    /** Recorrido transitivo generico: la arista prohibida se nombra a si misma, no la arista inicial permitida. */
    private static Rule isolatedFromModules(String root, String module, Set<String> forbidden, String description) {
        return classes -> {
            List<String> problems = new ArrayList<>();
            for (JavaClass origin : classes) {
                Location from = locate(origin.getPackageName(), root);
                if (from != null && from.module().equals(module)) {
                    problems.addAll(forbiddenPathsFrom(origin, root, forbidden));
                }
            }
            if (!problems.isEmpty()) {
                throw new AssertionError(description + ":\n" + String.join("\n", new LinkedHashSet<>(problems)));
            }
        };
    }

    /** Recorrido en anchura por las dependencias dentro del raiz; devuelve los caminos prohibidos. */
    private static Set<String> forbiddenPathsFrom(JavaClass origin, String root, Set<String> forbidden) {
        Set<String> problems = new LinkedHashSet<>();
        Map<JavaClass, List<String>> visited = new HashMap<>();
        Deque<JavaClass> pending = new ArrayDeque<>();
        for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
            collectForbidden(root, dependency, forbidden, List.of(origin.getName()), visited, pending, problems);
        }
        while (!pending.isEmpty()) {
            JavaClass current = pending.remove();
            List<String> path = visited.get(current);
            for (Dependency dependency : current.getDirectDependenciesFromSelf()) {
                collectForbidden(root, dependency, forbidden, path, visited, pending, problems);
            }
        }
        return problems;
    }

    /** Registra la violacion o habilita el siguiente paso del recorrido transitivo. */
    private static void collectForbidden(String root, Dependency dependency, Set<String> forbidden, List<String> path,
            Map<JavaClass, List<String>> visited, Deque<JavaClass> pending, Collection<String> problems) {
        JavaClass target = dependency.getTargetClass();
        Location to = locate(target.getPackageName(), root);
        if (to == null) {
            return;
        }
        if (forbidden.contains(to.module())) {
            List<String> fullPath = new ArrayList<>(path);
            fullPath.add(target.getName());
            problems.add(String.join(" -> ", fullPath) + " (modulo " + to.module() + ")");
            return;
        }
        if (visited.putIfAbsent(target, append(path, target.getName())) == null) {
            pending.add(target);
        }
    }

    private static List<String> append(List<String> path, String element) {
        List<String> result = new ArrayList<>(path);
        result.add(element);
        return List.copyOf(result);
    }

    /** Fuera de auditoria solo se puede referenciar su capa api: nunca entidades, adaptadores ni dominio. */
    public static Rule auditEventsExposedOnlyThroughApi(String root) {
        return dependencyRule(root, "los eventos de auditoria solo se exponen por su capa api", (from, dependency) -> {
            if (from.module().equals(AUDITORIA)) {
                return false;
            }
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            return to != null && to.module().equals(AUDITORIA) && !to.layer().equals(API);
        });
    }

    /** No hay ciclos entre modulos. */
    public static Rule noCycles(String root) {
        ArchRule rule = SlicesRuleDefinition.slices().matching(root + ".(*)..").should().beFreeOfCycles();
        return rule::check;
    }

    /** {@code domain} no depende de api/application/infrastructure ni de Spring (cualquier paquete) ni de java.sql. */
    public static Rule domainIndependent(String root) {
        return dependencyRule(root, "domain es independiente de las demas capas y del framework", (from, dependency) -> {
            if (!from.layer().equals(DOMAIN)) {
                return false;
            }
            String targetPackage = dependency.getTargetClass().getPackageName() + ".";
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            if (to != null) {
                return Set.of(API, APPLICATION, INFRASTRUCTURE).contains(to.layer());
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
            if (!from.layer().equals(APPLICATION)) {
                return false;
            }
            Location to = locate(dependency.getTargetClass().getPackageName(), root);
            return to != null
                    && to.module().equals(from.module())
                    && Set.of(API, INFRASTRUCTURE).contains(to.layer());
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

    /** La configuracion HTTP de seguridad vive solo en auth.infrastructure y nadie de fuera la consume (D11). */
    public static Rule securityConfinedToAuthInfrastructure(String root) {
        return classes -> {
            List<String> problems = new ArrayList<>();
            String authInfrastructure = root + "." + AUTH + "." + INFRASTRUCTURE;
            for (JavaClass javaClass : classes) {
                if (javaClass.isAnnotatedWith("org.springframework.security.config.annotation.web.configuration.EnableWebSecurity")
                        && !javaClass.getPackageName().startsWith(authInfrastructure)) {
                    problems.add("Seguridad HTTP fuera de " + authInfrastructure + ": " + javaClass.getName());
                }
                Location from = locate(javaClass.getPackageName(), root);
                if (from == null || from.module().equals(AUTH)) {
                    continue;
                }
                for (Dependency dependency : javaClass.getDirectDependenciesFromSelf()) {
                    Location to = locate(dependency.getTargetClass().getPackageName(), root);
                    if (to != null && to.module().equals(AUTH) && to.layer().equals(INFRASTRUCTURE)) {
                        problems.add(dependency.getDescription());
                    }
                }
            }
            if (!problems.isEmpty()) {
                throw new AssertionError("seguridad confinada en auth.infrastructure:\n"
                        + String.join("\n", new LinkedHashSet<>(problems)));
            }
        };
    }
}
