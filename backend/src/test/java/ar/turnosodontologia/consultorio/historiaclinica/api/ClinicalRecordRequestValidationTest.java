package ar.turnosodontologia.consultorio.historiaclinica.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClinicalRecordRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void anamnesisValidaPasaSinViolaciones() {
        AnamnesisRequest request = new AnamnesisRequest("penicilina", "ibuprofeno", null, null, "nota");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void anamnesisConUnSoloCampoYElLimiteExactoEsValida() {
        assertThat(validator.validate(new AnamnesisRequest(null, null, "x".repeat(2000), null, null))).isEmpty();
    }

    @Test
    void anamnesisConTodosLosCamposAusentesOEnBlancoEsInvalida() {
        assertThat(paths(validator.validate(new AnamnesisRequest(null, null, null, null, null))))
                .containsExactly("anyFieldPresent");
        assertThat(paths(validator.validate(new AnamnesisRequest(" ", "", "  ", "\t", " "))))
                .containsExactly("anyFieldPresent");
    }

    @Test
    void anamnesisRechazaCadaCampoQueExcedeLos2000Caracteres() {
        String tooLong = "x".repeat(2001);

        assertThat(paths(validator.validate(new AnamnesisRequest(tooLong, "ok", null, null, null))))
                .containsExactly("allergies");
        assertThat(paths(validator.validate(new AnamnesisRequest("ok", tooLong, tooLong, tooLong, tooLong))))
                .containsExactlyInAnyOrder("medications", "medicalConditions", "habits", "notes");
    }

    @Test
    void correccionValidaPasaSinViolaciones() {
        AnamnesisCorrectionRequest request = new AnamnesisCorrectionRequest(
                "alergia mal cargada", new AnamnesisRequest("latex", null, null, null, null));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void correccionRechazaMotivoAusenteVacioOEnBlanco() {
        AnamnesisRequest content = new AnamnesisRequest("latex", null, null, null, null);

        for (String reason : new String[] {null, "", "   "}) {
            assertThat(paths(validator.validate(new AnamnesisCorrectionRequest(reason, content))))
                    .containsExactly("reason");
        }
    }

    @Test
    void correccionRechazaMotivoDeMasDe500Caracteres() {
        AnamnesisRequest content = new AnamnesisRequest("latex", null, null, null, null);

        assertThat(paths(validator.validate(new AnamnesisCorrectionRequest("x".repeat(501), content))))
                .containsExactly("reason");
        assertThat(validator.validate(new AnamnesisCorrectionRequest("x".repeat(500), content))).isEmpty();
    }

    @Test
    void correccionExigeContenidoYLoValidaEnProfundidad() {
        assertThat(paths(validator.validate(new AnamnesisCorrectionRequest("motivo", null))))
                .containsExactly("content");
        assertThat(paths(validator.validate(new AnamnesisCorrectionRequest(
                "motivo", new AnamnesisRequest(null, null, null, null, null)))))
                .containsExactly("content.anyFieldPresent");
    }

    @Test
    void toStringNoImprimeContenidoNiMotivo() {
        AnamnesisRequest content = new AnamnesisRequest("penicilina", "ibuprofeno", "asma", "fuma", "nota privada");
        AnamnesisCorrectionRequest correction = new AnamnesisCorrectionRequest("motivo reservado", content);

        assertThat(content.toString()).doesNotContain("penicilina", "ibuprofeno", "asma", "fuma", "privada");
        assertThat(correction.toString()).doesNotContain("reservado", "penicilina");
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
    }
}
