package ar.turnosodontologia.consultorio.shared.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PracticeSettingsRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestValidoPasaSinViolaciones() {
        PracticeSettingsRequest request = new PracticeSettingsRequest(
                "Consultorio",
                "America/Argentina/Buenos_Aires",
                "test@test.com",
                "123456",
                "Calle 1",
                15,
                120,
                30,
                24);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void requestConNombreEnBlancoFalla() {
        PracticeSettingsRequest request = new PracticeSettingsRequest(
                "   ",
                "America/Argentina/Buenos_Aires",
                null,
                null,
                null,
                15,
                120,
                30,
                24);

        assertThat(validator.validate(request)).hasSize(1);
    }
}
