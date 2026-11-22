package ar.turnosodontologia.consultorio.agenda.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ServiceDurationPolicyTest {

    @Test
    void duracionMultiploDeLaGranularidadEsValida() {
        assertThatCode(() -> ServiceDurationPolicy.validate(45, 15)).doesNotThrowAnyException();
    }

    @Test
    void duracionIgualALaGranularidadEsValida() {
        assertThatCode(() -> ServiceDurationPolicy.validate(30, 30)).doesNotThrowAnyException();
        assertThatCode(() -> ServiceDurationPolicy.validate(60, 60)).doesNotThrowAnyException();
    }

    @Test
    void duracionNoMultiploDevuelveBusinessRuleException() {
        assertThatThrownBy(() -> ServiceDurationPolicy.validate(20, 15))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(e -> assertThat(((BusinessRuleException) e).code())
                        .isEqualTo("DURATION_NOT_MULTIPLE_OF_GRANULARITY"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -15})
    void duracionCeroYNegativaLlanzanExcepcion(int durationMin) {
        assertThatThrownBy(() -> ServiceDurationPolicy.validate(durationMin, 15))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void granularidadesConMultiplosYNoMultiplos() {
        int[] granularidades = {10, 15, 20, 30, 60};
        for (int granularity : granularidades) {
            assertThatCode(() -> ServiceDurationPolicy.validate(granularity, granularity))
                    .as("duracion igual a la granularidad %d", granularity)
                    .doesNotThrowAnyException();
            assertThatCode(() -> ServiceDurationPolicy.validate(granularity * 3, granularity))
                    .as("duracion multiplo de la granularidad %d", granularity)
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> ServiceDurationPolicy.validate(granularity + 5, granularity))
                    .as("duracion no multiplo de la granularidad %d", granularity)
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}
