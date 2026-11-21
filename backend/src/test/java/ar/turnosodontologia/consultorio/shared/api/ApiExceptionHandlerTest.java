package ar.turnosodontologia.consultorio.shared.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnosodontologia.probe.ProblemProbeController;
import org.junit.jupiter.api.BeforeEach;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Unitario del manejo global de errores (sin BD, sin contexto de Spring Boot): MockMvc standalone con el handler real
 * y un controller de prueba. Se usa standalone y no {@code @WebMvcTest} para no agregar la dependencia
 * {@code spring-boot-starter-webmvc-test} (no aprobada en C-02).
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ProblemProbeController())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void notFoundDeDominioEs404ConCodigoYTituloGenerico() throws Exception {
        mockMvc.perform(get("/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Recurso no encontrado"))
                .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void conflictoDeDominioEs409() throws Exception {
        mockMvc.perform(get("/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicto"))
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"));
    }

    @Test
    void reglaDeNegocioIncumplidaEs422() throws Exception {
        mockMvc.perform(get("/probe/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Regla de negocio incumplida"))
                .andExpect(jsonPath("$.code").value("APPOINTMENT_IN_PAST"));
    }

    @Test
    void violacionDeIntegridadEs409GenericoSinMensajeDeLaBase() throws Exception {
        String body = mockMvc.perform(get("/probe/integrity"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("dr@ejemplo.com").doesNotContain("app_user_email_key").doesNotContain("duplicate");
    }

    @Test
    void jsonMalFormadoEs400SinElDniNiElMensajeDelParser() throws Exception {
        String body = mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dni\":\"30123456\", "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("30123456").doesNotContain("dni").doesNotContain("JsonParseException");
    }

    @Test
    void validacionEs400ConFieldYCodigoPorCampoSinValoresEnviados() throws Exception {
        String body = mockMvc.perform(post("/probe/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\", \"note\":\"nota-demasiado-larga\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[?(@.field=='name')].code").value("NotBlank"))
                .andExpect(jsonPath("$.errors[?(@.field=='note')].code").value("Size"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("nota-demasiado-larga").doesNotContain("rejectedValue");
    }

    @Test
    void metodoNoSoportadoEs405() throws Exception {
        mockMvc.perform(post("/probe/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void parametroNumericoConTextoEs400SinElValor() throws Exception {
        String body = mockMvc.perform(get("/probe/number").param("n", "texto-secreto"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("texto-secreto");
    }

    @Test
    void tipoDeContenidoNoSoportadoEs415() throws Exception {
        mockMvc.perform(post("/probe/body").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rutaInexistenteEs404ConCodigoNotFound() throws Exception {
        mockMvc.perform(get("/probe/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void errorInesperadoEs500ConErrorIdYSinFiltrarElMensajeNiAlLog(CapturedOutput output) throws Exception {
        String body = mockMvc.perform(get("/probe/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.title").value("Error interno"))
                .andReturn().getResponse().getContentAsString();

        String errorId = com.jayway.jsonpath.JsonPath.read(body, "$.errorId");
        assertThat(UUID.fromString(errorId)).isNotNull();
        assertThat(body).doesNotContain("dr@ejemplo.com").doesNotContain("fallo para");
        assertThat(output.getAll())
                .contains(errorId)
                .contains("RuntimeException")
                .doesNotContain("dr@ejemplo.com")
                .doesNotContain("fallo para");
    }

    @Test
    void validacionDeParametrosDeMetodoEs400ConCodigoDeRestriccionSinElValor() throws Exception {
        String body = mockMvc.perform(get("/probe/constrained").param("q", "valor-largo-secreto"))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(org.springframework.web.method.annotation.HandlerMethodValidationException.class))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("q"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("valor-largo-secreto");
    }

    @Test
    void erroresDeDominioSinSubtipoConocidoSon400ConSuCodigo() throws Exception {
        mockMvc.perform(get("/probe/generic-domain"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("GENERIC_RULE"));
    }

    @Test
    void aceptarSoloUnTipoNoOfrecidoEs406() throws Exception {
        mockMvc.perform(get("/probe/only-json").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
    }

    @Test
    void estadosClienteNoListadosTienenCodigoGenericoDeSolicitud() throws Exception {
        mockMvc.perform(get("/probe/status").param("code", "413"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").doesNotExist());
    }

    @Test
    void estadosServidorNoListadosTienenCodigoDeErrorInterno() throws Exception {
        mockMvc.perform(get("/probe/status").param("code", "503"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void accessDeniedNoSeConvierteEn500ParaQueLoResuelvaSpringSecurity() {
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/denied")))
                .hasRootCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void erroresDeAutenticacionNoSeConviertenEn500ParaQueLosResuelvaElEntryPoint() {
        assertThatThrownBy(() -> mockMvc.perform(get("/probe/unauthenticated")))
                .hasRootCauseInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }
}
