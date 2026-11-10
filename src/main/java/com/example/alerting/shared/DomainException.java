package com.example.alerting.shared;

import java.util.Objects;

/**
 * A business-rule failure that the caller can understand and act on, such as an unknown category or a
 * missing permission.
 *
 * <p>The exception carries a {@link Kind} (the sort of failure, which the web layer maps to an HTTP
 * status) and a stable {@link ErrorCodes code} that clients can branch on. Unexpected failures are
 * <em>not</em> domain exceptions: they stay plain runtime exceptions and surface as a generic 500.
 */
public class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The sort of failure; the web layer decides the HTTP status. */
    public enum Kind {

        /** The request is wrong: bad input, or a reference to something that does not exist. */
        INVALID,

        /** The caller could not be identified. */
        UNAUTHENTICATED,

        /** The caller is identified but not allowed. */
        FORBIDDEN,

        /** The request is fine but conflicts with the current state of a resource. */
        CONFLICT
    }

    private final Kind kind;
    private final String code;

    /**
     * Creates the exception.
     *
     * @param kind the sort of failure
     * @param code the stable error code, see {@link ErrorCodes}
     * @param message a plain-English explanation that is safe to show to the caller
     * @throws IllegalArgumentException if the code or the message is blank
     */
    public DomainException(Kind kind, String code, String message) {
        super(Guards.requireNonBlank(message, "message"));
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = Guards.requireNonBlank(code, "code");
    }

    /**
     * The sort of failure.
     *
     * @return the kind
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * The stable error code that clients can branch on.
     *
     * @return the code, see {@link ErrorCodes}
     */
    public String getCode() {
        return code;
    }
}
