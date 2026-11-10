package com.example.alerting.shared;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Input that parsed correctly but breaks one or more field rules.
 *
 * <p>All problems found are reported together, so a client can fix them in one round trip. The web layer
 * renders the list as the {@code errors} array of the {@code VALIDATION_FAILED} problem response.
 */
public class ValidationException extends DomainException {

    private static final long serialVersionUID = 1L;

    // An exception is never serialized here; transient keeps the serial lint quiet without a custom type.
    private final transient List<FieldError> errors;

    /**
     * Creates the exception.
     *
     * @param errors every problem found, at least one
     * @throws IllegalArgumentException if the list is empty
     */
    public ValidationException(List<FieldError> errors) {
        super(Kind.INVALID, ErrorCodes.VALIDATION_FAILED, describe(errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Creates the exception for a single problem.
     *
     * @param field the offending field
     * @param message what is wrong and how to fix it
     * @return the exception, ready to throw
     */
    public static ValidationException of(String field, String message) {
        return new ValidationException(List.of(new FieldError(field, message)));
    }

    /**
     * The problems found.
     *
     * @return an unmodifiable list with at least one entry
     */
    public List<FieldError> getErrors() {
        return errors;
    }

    private static String describe(List<FieldError> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("A validation failure needs at least one field error");
        }
        return "Validation failed: "
                + errors.stream().map(error -> error.field() + " " + error.message()).collect(Collectors.joining("; "));
    }
}
