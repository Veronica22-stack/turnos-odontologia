package ar.turnosodontologia.consultorio.shared.api;

import ar.turnosodontologia.consultorio.shared.domain.BusinessRuleException;
import ar.turnosodontologia.consultorio.shared.domain.ConflictException;
import ar.turnosodontologia.consultorio.shared.domain.DomainException;
import ar.turnosodontologia.consultorio.shared.domain.ForbiddenException;
import ar.turnosodontologia.consultorio.shared.domain.NotFoundException;
import ar.turnosodontologia.consultorio.shared.domain.RateLimitedException;
import ar.turnosodontologia.consultorio.shared.domain.UnauthenticatedException;
import ar.turnosodontologia.consultorio.shared.domain.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo global de errores en formato {@code ProblemDetail} (RFC 9457) sin datos personales (design D10).
 *
 * <p>Ninguna respuesta lleva {@code detail} dinamico ni valores enviados; el log de errores inesperados lleva solo un
 * {@code errorId} y el nombre de la clase de la excepcion (su mensaje o causa podrian contener datos personales).
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String SCHEDULE_BLOCK_EXCLUSION = "schedule_block_no_overlap";
    private static final String SCHEDULE_BLOCK_OVERLAP = "SCHEDULE_BLOCK_OVERLAP";

    /** Un campo invalido: nombre del campo y nombre de la restriccion violada, nunca el valor. */
    public record FieldProblem(String field, String code) {
    }

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(DomainException error) {
        HttpStatus status = statusOf(error);
        return ResponseEntity.status(status).body(ProblemDetails.of(status, error.code()));
    }

    @ExceptionHandler(UnauthenticatedException.class)
    ResponseEntity<ProblemDetail> handleUnauthenticated(UnauthenticatedException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ProblemDetails.of(HttpStatus.UNAUTHORIZED, error.code()));
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> handleForbidden(ForbiddenException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ProblemDetails.of(HttpStatus.FORBIDDEN, error.code()));
    }

    @ExceptionHandler(RateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(RateLimitedException error) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(error.retryAfterSeconds()))
                .body(ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, error.code()));
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ProblemDetail> handleValidation(ValidationException error) {
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, error.code());
        problem.setProperty("errors", List.of(new FieldProblem(error.field(), error.constraint())));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException error) {
        // Rangos invertidos o instantes mal formados: el mensaje puede contener valores del cliente, no se devuelve.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetails.of(HttpStatus.BAD_REQUEST, "INVALID_RANGE"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException error) {
        // El mensaje de PostgreSQL incluye valores; solo se inspecciona el nombre de la restriccion para elegir el code.
        String code = isScheduleBlockOverlap(error) ? SCHEDULE_BLOCK_OVERLAP : "CONFLICT";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetails.of(HttpStatus.CONFLICT, code));
    }

    private static boolean isScheduleBlockOverlap(DataIntegrityViolationException error) {
        Throwable cause = error.getMostSpecificCause();
        String message = cause == null ? null : cause.getMessage();
        return message != null && message.contains(SCHEDULE_BLOCK_EXCLUSION);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception error) throws Exception {
        // Spring Security resuelve 401/403 (ExceptionTranslationFilter + entry point): no se convierten en 500.
        if (error instanceof AccessDeniedException || error instanceof AuthenticationException) {
            throw error;
        }
        UUID errorId = UUID.randomUUID();
        LOG.error("Error interno errorId={} tipo={}", errorId, error.getClass().getName());
        ProblemDetail problem = ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        problem.setProperty("errorId", errorId.toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> problems = new ArrayList<>();
        for (ObjectError objectError : error.getBindingResult().getAllErrors()) {
            String field = objectError instanceof FieldError fieldError ? fieldError.getField() : objectError.getObjectName();
            problems.add(new FieldProblem(field, objectError.getCode()));
        }
        return validationResponse(error, problems, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException error, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> problems = new ArrayList<>();
        error.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
                String field = resolvable instanceof FieldError fieldError ? fieldError.getField() : parameter;
                problems.add(new FieldProblem(field, lastCode(resolvable)));
            }
        });
        return validationResponse(error, problems, headers, status, request);
    }

    /** Reemplaza el {@code detail} del framework (puede incluir valores enviados o texto del parser) por un codigo. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception error, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        boolean problem = body instanceof ProblemDetail || (body == null && error instanceof ErrorResponse);
        Object sanitized = problem ? ProblemDetails.of(status, codeFor(status)) : body;
        return super.handleExceptionInternal(error, sanitized, headers, status, request);
    }

    private ResponseEntity<Object> validationResponse(
            Exception error, List<FieldProblem> problems, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetails.of(status, "VALIDATION_FAILED");
        problem.setProperty("errors", problems);
        return super.handleExceptionInternal(error, problem, headers, status, request);
    }

    private static String lastCode(MessageSourceResolvable resolvable) {
        String[] codes = resolvable.getCodes();
        return codes == null || codes.length == 0 ? "INVALID" : codes[codes.length - 1];
    }

    private static HttpStatus statusOf(DomainException error) {
        if (error instanceof NotFoundException) {
            return HttpStatus.NOT_FOUND;
        }
        if (error instanceof ConflictException) {
            return HttpStatus.CONFLICT;
        }
        if (error instanceof BusinessRuleException) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 400 -> "MALFORMED_REQUEST";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "BAD_REQUEST";
        };
    }
}
