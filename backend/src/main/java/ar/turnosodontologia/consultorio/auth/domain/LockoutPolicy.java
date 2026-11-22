package ar.turnosodontologia.consultorio.auth.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Umbrales y duraciones del bloqueo progresivo (design D4); sin Spring ni persistencia. */
public record LockoutPolicy(List<Integer> thresholds, List<Duration> durations) {

    private static final List<Integer> DEFAULT_THRESHOLDS = List.of(5, 10, 15);
    private static final List<Duration> DEFAULT_DURATIONS =
            List.of(Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofMinutes(60));

    public LockoutPolicy {
        if (thresholds == null || durations == null || thresholds.size() != durations.size() || thresholds.isEmpty()) {
            throw new IllegalArgumentException("La política de bloqueo necesita umbrales y duraciones del mismo tamaño");
        }
        thresholds = List.copyOf(thresholds);
        durations = List.copyOf(durations);
    }

    public static LockoutPolicy defaults() {
        return new LockoutPolicy(DEFAULT_THRESHOLDS, DEFAULT_DURATIONS);
    }

    /** Duración que corresponde al número de fallos ya contabilizado; vacío si aún no hay bloqueo. */
    public Optional<Duration> durationFor(int failedAttempts) {
        if (failedAttempts >= thresholds.get(thresholds.size() - 1)) {
            return Optional.of(maximumDuration());
        }
        for (int index = 0; index < thresholds.size() - 1; index++) {
            if (failedAttempts == thresholds.get(index)) {
                return Optional.of(durations.get(index));
            }
        }
        return Optional.empty();
    }

    /** El techo nunca es permanente: la última duración se aplica a todos los fallos posteriores. */
    public Duration maximumDuration() {
        return Objects.requireNonNull(durations.get(durations.size() - 1));
    }
}
