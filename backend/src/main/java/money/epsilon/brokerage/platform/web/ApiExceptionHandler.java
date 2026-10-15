package money.epsilon.brokerage.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import money.epsilon.brokerage.platform.activity.ErrorLogService;
import money.epsilon.brokerage.platform.logging.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Every error answer is an RFC 7807 problem with a {@code reference} (the request ID, also in every log line).
 * Anything unexpected becomes a 500 that says only "something went wrong, quote reference X"; the details go to
 * the log and the error log, never to the screen. Spring's own errors (bad JSON, missing parameter, file too big…)
 * keep their status via {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public static final String REFERENCE = "reference";

    private final ErrorLogService errors;

    ApiExceptionHandler(ErrorLogService errors) {
        this.errors = errors;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RuleViolationException.class)
    ProblemDetail ruleViolation(RuleViolationException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Unique/check constraints that raced past a service-level check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataConflict(DataIntegrityViolationException e) {
        log.info("Data conflict: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "The change conflicts with existing data");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Method security (@PreAuthorize) inside a controller: a plain 403, not a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "You do not have access to this");
    }

    /** The last net: log everything, show nothing but the reference. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        String reference = RequestContext.requestIdOrNew();
        log.error("Unexpected error", e);
        errors.record(reference, request.getMethod(), request.getRequestURI(), 500,
                e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Please share reference " + reference + " with the support team.", reference);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Some fields are invalid");
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().headers(headers).body(problem);
    }

    /** Spring's built-in errors get the reference too. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode status,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail p) p.setProperty(REFERENCE, RequestContext.requestId());
        return super.createResponseEntity(body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return problem(status, detail, RequestContext.requestId());
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String reference) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setProperty(REFERENCE, reference);
        return p;
    }
}
