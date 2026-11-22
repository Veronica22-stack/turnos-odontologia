package ar.turnosodontologia.consultorio.historiaclinica.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OdontogramRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static OdontogramEntryRequest entry(String tooth, String surface, String condition, String notes) {
        return new OdontogramEntryRequest(tooth, surface, condition, notes);
    }

    private static OdontogramRequest batch(OdontogramEntryRequest... entries) {
        return new OdontogramRequest(List.of(entries));
    }

    @Test
    void unLoteValidoPasaSinViolaciones() {
        OdontogramRequest request = batch(
                entry("16", "OCCLUSAL", "CARIES", "mancha"), entry("21", null, "CROWN", null),
                entry("51", null, "HEALTHY", null), entry("48", null, "MISSING", "x".repeat(500)));

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void unLoteDe64EstadosEsValidoYUnoDe65No() {
        assertThat(validator.validate(new OdontogramRequest(distinctEntries(64)))).isEmpty();
        assertThat(paths(validator.validate(new OdontogramRequest(distinctEntries(65)))))
                .containsExactly("entries");
    }

    @Test
    void rechazaLoteAusenteOVacio() {
        assertThat(paths(validator.validate(new OdontogramRequest(null)))).containsExactly("entries");
        assertThat(paths(validator.validate(new OdontogramRequest(List.of())))).containsExactly("entries");
        assertThat(paths(validator.validate(new OdontogramRequest(Collections.singletonList(null)))))
                .isNotEmpty()
                .allMatch(path -> path.startsWith("entries"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"19", "56", "59", "90", "00", "1", "111", "10", "49", "86", "ab", " 11"})
    void rechazaPiezasFueraDeLaNotacionFdi(String tooth) {
        Set<ConstraintViolation<OdontogramRequest>> violations =
                validator.validate(batch(entry(tooth, null, "CARIES", null)));

        assertThat(paths(violations)).containsExactly("entries[0].toothCode");
        assertThat(violations).allSatisfy(v -> assertThat(v.getMessage()).doesNotContain("111"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FRACTURED", "caries", "", " "})
    void rechazaEstadosFueraDelCatalogo(String condition) {
        assertThat(paths(validator.validate(batch(entry("11", null, condition, null)))))
                .containsExactly("entries[0].condition");
        assertThat(paths(validator.validate(batch(entry("11", null, null, null)))))
                .containsExactly("entries[0].condition");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CERVICAL", "mesial", ""})
    void rechazaCarasFueraDelCatalogo(String surface) {
        assertThat(paths(validator.validate(batch(entry("11", surface, "CARIES", null)))))
                .containsExactly("entries[0].surface");
    }

    @ParameterizedTest
    @ValueSource(strings = {"HEALTHY", "MISSING", "CROWN", "ROOT_CANAL"})
    void rechazaCaraConEstadoDePiezaCompleta(String condition) {
        Set<ConstraintViolation<OdontogramRequest>> violations =
                validator.validate(batch(entry("11", "MESIAL", condition, null)));

        assertThat(paths(violations)).containsExactly("entries[0].surface");
        assertThat(violations).singleElement().satisfies(v -> assertThat(
                v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName())
                .isEqualTo("SurfaceAllowedForCondition"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CARIES", "FILLED"})
    void aceptaCaraConCariesYObturacion(String condition) {
        assertThat(validator.validate(batch(entry("11", "DISTAL", condition, null)))).isEmpty();
    }

    @Test
    void rechazaPosicionRepetidaEnElLoteSoloSiCoincidenPiezaYCara() {
        assertThat(paths(validator.validate(batch(
                entry("36", "OCCLUSAL", "CARIES", null), entry("36", "OCCLUSAL", "FILLED", null)))))
                .containsExactly("entries");
        assertThat(paths(validator.validate(batch(
                entry("36", null, "MISSING", null), entry("36", null, "CROWN", null)))))
                .containsExactly("entries");

        assertThat(validator.validate(batch(
                entry("36", "OCCLUSAL", "CARIES", null), entry("36", "MESIAL", "CARIES", null),
                entry("36", null, "CROWN", null), entry("37", "OCCLUSAL", "CARIES", null)))).isEmpty();
    }

    @Test
    void rechazaNotasDeMasDe500Caracteres() {
        assertThat(paths(validator.validate(batch(entry("11", null, "CARIES", "x".repeat(501))))))
                .containsExactly("entries[0].notes");
    }

    @Test
    void correccionValidaPasaSinViolaciones() {
        assertThat(validator.validate(new OdontogramCorrectionRequest("pieza sana", "HEALTHY", null))).isEmpty();
        assertThat(validator.validate(new OdontogramCorrectionRequest("x".repeat(500), "FILLED", "x".repeat(500))))
                .isEmpty();
    }

    @Test
    void correccionRechazaMotivoAusenteVacioOEnBlancoOExcedido() {
        for (String reason : new String[] {null, "", "   ", "x".repeat(501)}) {
            assertThat(paths(validator.validate(new OdontogramCorrectionRequest(reason, "HEALTHY", null))))
                    .containsExactly("reason");
        }
    }

    @Test
    void correccionRechazaEstadoFueraDelCatalogoYNotasExcedidas() {
        assertThat(paths(validator.validate(new OdontogramCorrectionRequest("m", "FRACTURED", null))))
                .containsExactly("condition");
        assertThat(paths(validator.validate(new OdontogramCorrectionRequest("m", null, null))))
                .containsExactly("condition");
        assertThat(paths(validator.validate(new OdontogramCorrectionRequest("m", "HEALTHY", "x".repeat(501)))))
                .containsExactly("notes");
    }

    @Test
    void toStringNoExponeNotasNiMotivo() {
        assertThat(entry("16", "OCCLUSAL", "CARIES", "NOTA-SECRETA").toString())
                .doesNotContain("NOTA-SECRETA", "16", "CARIES");
        assertThat(batch(entry("16", null, "CARIES", "NOTA-SECRETA")).toString()).doesNotContain("NOTA-SECRETA");
        assertThat(new OdontogramCorrectionRequest("MOTIVO-SECRETO", "HEALTHY", "NOTA-SECRETA").toString())
                .doesNotContain("MOTIVO-SECRETO", "NOTA-SECRETA");
    }

    /** Lote de n posiciones distintas: 32 piezas completas, 32 caras oclusales y una cara mesial. */
    private static List<OdontogramEntryRequest> distinctEntries(int n) {
        String[] teeth = IntStream.rangeClosed(1, 4).boxed()
                .flatMap(q -> IntStream.rangeClosed(1, 8).mapToObj(t -> q + "" + t)).toArray(String[]::new);
        return IntStream.range(0, n).mapToObj(i -> i < 32
                ? entry(teeth[i], null, "HEALTHY", null)
                : i < 64 ? entry(teeth[i - 32], "OCCLUSAL", "CARIES", null) : entry("11", "MESIAL", "CARIES", null))
                .toList();
    }

    private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }
}
