package ar.turnosodontologia.consultorio.historiaclinica.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EvolutionRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void unaEvolucionValidaConYSinTurnoPasaSinViolaciones() {
        assertThat(validator.validate(new EvolutionRequest("Control de rutina", null))).isEmpty();
        assertThat(validator.validate(new EvolutionRequest("Obturacion", UUID.randomUUID().toString()))).isEmpty();
        assertThat(validator.validate(new EvolutionRequest("x".repeat(5000), null))).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\n\t "})
    void rechazaDescripcionAusenteOEnBlanco(String description) {
        assertThat(paths(validator.validate(new EvolutionRequest(description, null))))
                .containsExactly("description");
    }

    @Test
    void rechazaDescripcionDe5001Caracteres() {
        assertThat(paths(validator.validate(new EvolutionRequest("x".repeat(5001), null))))
                .containsExactly("description");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "1234", "00000000-0000-0000-0000-00000000000", "not-a-uuid-at-all",
            "00000000-0000-0000-0000-00000000000g", " 00000000-0000-0000-0000-000000000000"})
    void rechazaUnTurnoMalFormado(String appointmentId) {
        Set<ConstraintViolation<EvolutionRequest>> violations =
                validator.validate(new EvolutionRequest("texto", appointmentId));

        assertThat(paths(violations)).containsExactly("appointmentId");
    }

    @Test
    void laCorreccionValidaPasaYRechazaMotivoYDescripcionInvalidos() {
        assertThat(validator.validate(new EvolutionCorrectionRequest("error de tipeo", "texto"))).isEmpty();
        assertThat(validator.validate(new EvolutionCorrectionRequest("x".repeat(500), "x".repeat(5000)))).isEmpty();

        assertThat(paths(validator.validate(new EvolutionCorrectionRequest(null, "texto"))))
                .containsExactly("reason");
        assertThat(paths(validator.validate(new EvolutionCorrectionRequest("   ", "texto"))))
                .containsExactly("reason");
        assertThat(paths(validator.validate(new EvolutionCorrectionRequest("x".repeat(501), "texto"))))
                .containsExactly("reason");
        assertThat(paths(validator.validate(new EvolutionCorrectionRequest("motivo", null))))
                .containsExactly("description");
        assertThat(paths(validator.validate(new EvolutionCorrectionRequest("motivo", "  "))))
                .containsExactly("description");
        assertThat(paths(validator.validate(new EvolutionCorrectionRequest("motivo", "x".repeat(5001)))))
                .containsExactly("description");
    }

    @Test
    void laCorreccionNoDeclaraUnCampoDeTurno() {
        assertThat(EvolutionCorrectionRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("reason", "description");
    }

    @Test
    void toStringDeSolicitudesYRespuestasNoRevelaTextoClinico() {
        UUID id = UUID.randomUUID();
        EvolutionEntryResponse response = new EvolutionEntryResponse(
                id, "descripcion-secreta", UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null,
                "motivo-secreto", null);

        assertThat(new EvolutionRequest("descripcion-secreta", null).toString()).doesNotContain("secreta");
        assertThat(new EvolutionCorrectionRequest("motivo-secreto", "descripcion-secreta").toString())
                .doesNotContain("secret");
        assertThat(response.toString()).doesNotContain("secret").contains(id.toString());
        assertThat(new EvolutionsResponse(java.util.List.of(response)).toString()).doesNotContain("secret");
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }
}
