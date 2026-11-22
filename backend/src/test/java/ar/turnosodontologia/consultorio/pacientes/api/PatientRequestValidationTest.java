package ar.turnosodontologia.consultorio.pacientes.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class PatientRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createRequestValidoPasaSinViolaciones() {
        CreatePatientRequest request = new CreatePatientRequest(
                "Juan", "Perez", "12345678", "11223344", "juan@test.com", "1990-05-20", "Calle 1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createRequestRechazaCamposObligatoriosEnBlanco() {
        CreatePatientRequest request = new CreatePatientRequest(
                "  ", "Perez", "12345678", "  ", null, null, null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("firstName", "phone");
    }

    @Test
    void updateRequestValidoPasaSinViolaciones() {
        UpdatePatientRequest request = new UpdatePatientRequest(
                "Juan", "Perez", "11223344", "juan@test.com", "1990-05-20", "Calle 1");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void updateRequestRechazaNombreApellidoYTelefonoEnBlanco() {
        UpdatePatientRequest request = new UpdatePatientRequest(
                "", "  ", "  ", null, null, null);

        assertThat(validator.validate(request)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("firstName", "lastName", "phone");
    }
}
