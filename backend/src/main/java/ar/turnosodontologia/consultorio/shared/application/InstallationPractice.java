package ar.turnosodontologia.consultorio.shared.application;

import ar.turnosodontologia.consultorio.shared.domain.Practice;
import ar.turnosodontologia.consultorio.shared.domain.PracticeRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Consultorio de la instalacion: identificador y zona horaria para todos los modulos (design D8). Se lee una sola
 * vez (perezoso) y se guarda para toda la vida del proceso; si la zona pasa a ser editable (C-05) hay que invalidarlo.
 */
@Component
public class InstallationPractice {

    private final PracticeRepository practices;
    private volatile Resolved resolved;

    public InstallationPractice(PracticeRepository practices) {
        this.practices = practices;
    }

    private record Resolved(UUID id, ZoneId zone) {
    }

    public UUID id() {
        return resolve().id();
    }

    public ZoneId zone() {
        return resolve().zone();
    }

    private Resolved resolve() {
        Resolved current = resolved;
        if (current == null) {
            synchronized (this) {
                current = resolved;
                if (current == null) {
                    current = load();
                    resolved = current;
                }
            }
        }
        return current;
    }

    private Resolved load() {
        List<Practice> rows = practices.findAll();
        if (rows.isEmpty()) {
            throw new IllegalStateException("Configuracion invalida: falta el consultorio de la instalacion");
        }
        if (rows.size() > 1) {
            throw new IllegalStateException("Configuracion invalida: hay mas de un consultorio en la instalacion");
        }
        Practice practice = rows.get(0);
        try {
            return new Resolved(practice.id(), ZoneId.of(practice.timezone()));
        } catch (DateTimeException error) {
            throw new IllegalStateException("Configuracion invalida: la zona horaria del consultorio es invalida");
        }
    }
}
