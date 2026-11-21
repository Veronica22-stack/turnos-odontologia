package ar.turnosodontologia.probe;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.ErrorResponseException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Controller de prueba que lanza cada tipo de excepcion. Vive FUERA del paquete escaneado por la aplicacion
 * ({@code ar.turnosodontologia.consultorio}) a proposito: en Spring 7 un handler necesita {@code @Controller}, y asi no
 * aparece en los contextos completos de los IT. Solo se
 * registra a mano en {@code ApiExceptionHandlerTest}.
 */
@Controller
@RequestMapping("/probe")
@ResponseBody
public class ProblemProbeController {

    public record ProbeBody(@NotBlank String name, @Size(max = 5) String note) {
    }

    @GetMapping("/not-found")
    String notFound() {
        throw new NotFoundException("PATIENT_NOT_FOUND");
    }

    @GetMapping("/conflict")
    public String conflict() {
        throw new ConflictException("SLOT_TAKEN");
    }

    @GetMapping("/rule")
    public String rule() {
        throw new BusinessRuleException("APPOINTMENT_IN_PAST");
    }

    @GetMapping("/integrity")
    public String integrity() {
        throw new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"app_user_email_key\" Key (email)=(dr@ejemplo.com)");
    }

    @PostMapping(value = "/body", consumes = MediaType.APPLICATION_JSON_VALUE)
    public String body(@Valid @RequestBody ProbeBody body) {
        return "ok";
    }

    @GetMapping("/number")
    public String number(@RequestParam int n) {
        return "ok";
    }

    @GetMapping("/constrained")
    String constrained(@RequestParam @Size(max = 3) String q) {
        return "ok";
    }

    @GetMapping("/unexpected")
    public String unexpected() {
        throw new RuntimeException("fallo para dr@ejemplo.com");
    }

    @GetMapping("/generic-domain")
    public String genericDomain() {
        throw new DomainException("GENERIC_RULE") {
        };
    }

    @GetMapping(value = "/only-json", produces = MediaType.APPLICATION_JSON_VALUE)
    public String onlyJson() {
        return "{}";
    }

    @GetMapping("/status")
    public String status(@RequestParam int code) {
        throw new ErrorResponseException(HttpStatusCode.valueOf(code));
    }

    @GetMapping("/denied")
    public String denied() {
        throw new AccessDeniedException("sin permiso para dr@ejemplo.com");
    }

    @GetMapping("/unauthenticated")
    public String unauthenticated() {
        throw new BadCredentialsException("credenciales de dr@ejemplo.com");
    }
}
