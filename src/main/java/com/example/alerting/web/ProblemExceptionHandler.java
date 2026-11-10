package com.example.alerting.web;

import com.example.alerting.shared.DomainException;
import com.example.alerting.shared.ErrorCodes;
import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.Text;
import com.example.alerting.shared.ValidationException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every failure into an {@code application/problem+json} response (RFC 9457) with a stable shape:
 * {@code type}, {@code title}, {@code status}, {@code detail}, a machine-readable {@code code}, and for
 * invalid input an {@code errors} list of {@code field} and {@code message}.
 *
 * <p>What clients see:
 *
 * <ul>
 *   <li>Business failures ({@link DomainException}) keep their own code and map to 400, 401, 403 or 409.</li>
 *   <li>A body that cannot be read, has an unknown field, or has a value of the wrong type is a 400 that
 *       names the field.</li>
 *   <li>An oversized body is a 413; an unsupported content type a 415; an unknown path a 404.</li>
 *   <li>A database that is down or timing out is a 503 with {@code Retry-After}: nothing was changed, so
 *       the sender may retry safely.</li>
 *   <li>Anything else is a 500 with a generic message. Exception messages, class names and stack traces go
 *       to the log, never to the client (docs/03, section 3).</li>
 * </ul>
 */
@RestControllerAdvice
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    /** Seconds a client is told to wait before retrying when a dependency is down. */
    static final String RETRY_AFTER_SECONDS = "5";

    private static final String CODE = "code";
    private static final String ERRORS = "errors";
    private static final int SHOWN_NAME_LENGTH = 100;
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * Renders field validation failures.
     *
     * @param ex the failure with its field errors
     * @return a 400 response listing the errors
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ex.getCode(),
                "The request has " + ex.getErrors().size() + " invalid field(s); see 'errors'.");
        problem.setProperty(ERRORS, ex.getErrors());
        return respond(problem);
    }

    /**
     * Renders business failures with their own code.
     *
     * @param ex the failure
     * @return a response whose status follows the failure's kind
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Object> handleDomain(DomainException ex) {
        return respond(problem(statusOf(ex.getKind()), ex.getCode(), ex.getMessage()));
    }

    /**
     * Renders an oversized request body.
     *
     * @param ex the failure
     * @return a 413 response
     */
    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<Object> handleTooLarge(RequestBodyTooLargeException ex) {
        return respond(problem(HttpStatus.CONTENT_TOO_LARGE, ErrorCodes.PAYLOAD_TOO_LARGE,
                "The request body must not be larger than " + ex.getMaxBytes() + " bytes."));
    }

    /**
     * Renders a temporarily unavailable dependency, in practice the database.
     *
     * @param ex the failure; it is logged, not shown
     * @return a 503 response with a {@code Retry-After} header
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            TransientDataAccessException.class})
    public ResponseEntity<Object> handleUnavailable(Exception ex) {
        LOG.warn("A dependency is unavailable: {}", ex.toString());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.SERVICE_UNAVAILABLE,
                "The service is temporarily unavailable. Nothing was changed, so it is safe to retry shortly.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * The last resort for failures nobody anticipated.
     *
     * @param ex the failure; it is logged with its stack trace, never shown
     * @return a 500 response with a generic message
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        LOG.error("Unexpected error while handling a request", ex);
        return respond(problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL_ERROR,
                "An unexpected error occurred."));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        RequestBodyTooLargeException tooLarge = findCause(ex, RequestBodyTooLargeException.class);
        if (tooLarge != null) {
            return handleTooLarge(tooLarge);
        }
        JacksonException jackson = findCause(ex, JacksonException.class);
        if (jackson instanceof UnrecognizedPropertyException) {
            return invalidField(fieldName(jackson), "is not a known field");
        }
        if (jackson instanceof MismatchedInputException mismatched && !jackson.getPath().isEmpty()) {
            return invalidField(fieldName(jackson), "must be " + describe(mismatched.getTargetType()));
        }
        return respond(problem(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST,
                "The request body is missing or is not valid JSON."));
    }

    /** Gives every standard Spring MVC error the same problem shape, with a stable code. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Map<String, Object> properties = problem.getProperties();
            if (properties == null || !properties.containsKey(CODE)) {
                String code = codeFor(statusCode);
                problem.setType(typeOf(code));
                problem.setProperty(CODE, code);
            }
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        return response;
    }

    private static ResponseEntity<Object> invalidField(String field, String message) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED,
                "The request has 1 invalid field(s); see 'errors'.");
        problem.setProperty(ERRORS, List.of(new FieldError(field, message)));
        return respond(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(typeOf(code));
        problem.setProperty(CODE, code);
        return problem;
    }

    private static ResponseEntity<Object> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** A stable, non-resolvable identifier of the problem type, derived from the code. */
    private static URI typeOf(String code) {
        return URI.create("urn:alerting:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    private static HttpStatus statusOf(DomainException.Kind kind) {
        return switch (kind) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
    }

    private static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCodes.MALFORMED_REQUEST;
            case 404 -> ErrorCodes.NOT_FOUND;
            case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCodes.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
            case 503 -> ErrorCodes.SERVICE_UNAVAILABLE;
            default -> "HTTP_" + status.value();
        };
    }

    /** Builds a field name such as {@code attributes.depthKm} or {@code items[2].name} from a JSON path. */
    private static String fieldName(JacksonException exception) {
        StringBuilder name = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getPropertyName() != null) {
                if (!name.isEmpty()) {
                    name.append('.');
                }
                name.append(reference.getPropertyName());
            } else if (reference.getIndex() >= 0) {
                name.append('[').append(reference.getIndex()).append(']');
            }
        }
        // The name comes from the client: make it safe to echo back.
        return Text.printable(name.toString(), SHOWN_NAME_LENGTH);
    }

    private static String describe(Class<?> targetType) {
        if (targetType == null) {
            return "a value of the right type";
        }
        if (targetType == Integer.class || targetType == int.class || targetType == Long.class
                || targetType == long.class || targetType == Short.class || targetType == short.class) {
            return "a whole number";
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return "true or false";
        }
        if (targetType == String.class) {
            return "text";
        }
        if (Map.class.isAssignableFrom(targetType)) {
            return "an object";
        }
        return "a value of the right type";
    }

    private static <T extends Throwable> T findCause(Throwable start, Class<T> type) {
        Throwable current = start;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
