package ar.turnosodontologia.consultorio.historiaclinica.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

class AnamnesisContentTest {

    @Test
    void rechazaAnamnesisConTodosLosCamposAusentesOEnBlanco() {
        assertThatThrownBy(() -> AnamnesisContent.of(null, null, null, null, null))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> AnamnesisContent.of(" ", "", "  ", "\t", " "))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rechazaCampoExcedidoIndicandoElCampo() {
        assertThatThrownBy(() -> AnamnesisContent.of("a".repeat(2001), null, null, null, null))
                .isInstanceOf(ValidationException.class)
                .satisfies(error -> assertThat(((ValidationException) error).field()).isEqualTo("allergies"));
        assertThatThrownBy(() -> AnamnesisContent.of("ok", null, null, null, "n".repeat(2001)))
                .isInstanceOf(ValidationException.class)
                .satisfies(error -> assertThat(((ValidationException) error).field()).isEqualTo("notes"));
    }

    @Test
    void aceptaUnSoloCampoYElLimiteExacto() {
        AnamnesisContent content = AnamnesisContent.of("a".repeat(2000), null, null, null, null);

        assertThat(content.allergies()).hasSize(2000);
        assertThat(content.medications()).isNull();
        assertThat(content.schemaVersion()).isEqualTo(AnamnesisContent.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void normalizaEnBlancoANuloYRecortaEspacios() {
        AnamnesisContent content = AnamnesisContent.of("  penicilina ", "   ", "diabetes", null, "");

        assertThat(content.allergies()).isEqualTo("penicilina");
        assertThat(content.medications()).isNull();
        assertThat(content.medicalConditions()).isEqualTo("diabetes");
        assertThat(content.notes()).isNull();
    }

    @Test
    void toStringNoContieneElContenidoClinico() {
        AnamnesisContent content = AnamnesisContent.of("penicilina", "ibuprofeno", "asma", "fuma", "nota privada");

        assertThat(content.toString()).doesNotContain("penicilina", "ibuprofeno", "asma", "fuma", "privada");
    }
}
