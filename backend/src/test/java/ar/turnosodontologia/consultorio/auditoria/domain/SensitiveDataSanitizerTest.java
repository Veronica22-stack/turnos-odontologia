package ar.turnosodontologia.consultorio.auditoria.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** La sanitizacion es pura y se aplica antes de persistir o loguear (design D3). */
class SensitiveDataSanitizerTest {

    @Test
    void contenidoClinicoYDatosPersonalesSeDescartanCompletamente() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("clinical_text", "evolucion del paciente con dolor persistente");
        details.put("dni", "30123456");
        details.put("email", "dr@ejemplo.com");
        details.put("phone", "1145678900");
        details.put("full_name", "Juan Perez");
        details.put("outcome", "OK");

        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(details);

        assertThat(sanitized).containsOnlyKeys("outcome");
        assertThat(sanitized.toString()).doesNotContain("evolucion", "30123456", "dr@ejemplo.com",
                "1145678900", "Juan Perez");
    }

    @Test
    void clavesAnidadasSeNormalizanYLosValoresPermitidosSensiblesSeRedactan() {
        Map<String, Object> anidado = new LinkedHashMap<>();
        anidado.put("Reason-Code", "OK");
        anidado.put("email", "dr@ejemplo.com");
        anidado.put("attempt", 30123456);
        anidado.put("source", "eyJhbGciOiJIUzI1NiJ9.payloadBase64UrlLargo1234567890");

        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(Map.of("source", anidado));

        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) sanitized.get("source");
        assertThat(inner).containsKey("reason_code");
        assertThat(inner).doesNotContainKey("email");
        assertThat(inner.get("reason_code")).isEqualTo("OK");
        assertThat(inner.get("attempt")).isEqualTo(SensitiveDataSanitizer.REDACTED);
        assertThat(inner.get("source")).isEqualTo(SensitiveDataSanitizer.REDACTED);
        assertThat(sanitized.toString()).doesNotContain("dr@ejemplo.com", "30123456", "eyJhbGciOi");
    }

    @Test
    void profundidadMayorADosYLimiteDeEntradasSeDescartan() {
        Map<String, Object> profundo = Map.of("outcome", Map.of("reason_code", Map.of("attempt", "secreto-profundo")));
        List<Object> muchos = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            muchos.add("campo-" + i);
        }
        muchos.add(new Object());

        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(
                Map.of("source", profundo, "field_names", muchos));

        assertThat(sanitized.toString()).doesNotContain("secreto-profundo");
        @SuppressWarnings("unchecked")
        List<Object> fields = (List<Object>) sanitized.get("field_names");
        assertThat(fields).hasSize(20).allMatch(element -> element instanceof String);
    }

    @Test
    void referenciasClinicasPermitidasConservanTipoVersionYEntradaCorregida() {
        String corrected = "01900000-0000-7000-8000-0000000000aa";
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("entry_type", "ANAMNESIS");
        details.put("version", 3);
        details.put("corrects_entry_id", corrected);
        details.put("content", "penicilina");
        details.put("reason", "alergia mal cargada");

        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(details);

        assertThat(sanitized).containsOnlyKeys("entry_type", "version", "corrects_entry_id");
        assertThat(sanitized.get("entry_type")).isEqualTo("ANAMNESIS");
        assertThat(sanitized.get("version")).isEqualTo("3");
        assertThat(sanitized.get("corrects_entry_id")).isEqualTo(corrected);
    }

    @Test
    void laEntradaCorregidaSoloSeConservaSiTieneFormaDeUuidYEnOtraClaveSigueRedactado() {
        Map<String, Object> notUuid = Map.of("corrects_entry_id", "eyJhbGciOiJIUzI1NiJ9.payloadBase64UrlLargo1234567890");
        Map<String, Object> uuidInOtherKey = Map.of("source", "01900000-0000-7000-8000-0000000000aa");

        assertThat(SensitiveDataSanitizer.sanitize(notUuid).get("corrects_entry_id"))
                .isEqualTo(SensitiveDataSanitizer.REDACTED);
        assertThat(SensitiveDataSanitizer.sanitize(uuidInOtherKey).get("source"))
                .isEqualTo(SensitiveDataSanitizer.REDACTED);
    }

    @Test
    void credencialesDiagnosticosYTokensTambienSeDescartan() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("password", "secreto");
        details.put("authorization", "Bearer abc.def.ghi");
        details.put("token", "base64token");
        details.put("diagnostico", "caries");
        details.put("reason_code", "INVALID_INPUT");

        Map<String, Object> sanitized = SensitiveDataSanitizer.sanitize(details);

        assertThat(sanitized).containsOnlyKeys("reason_code");
        assertThat(sanitized.toString()).doesNotContain("secreto", "Bearer", "base64token", "caries");
    }
}
