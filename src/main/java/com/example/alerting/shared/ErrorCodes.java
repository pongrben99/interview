package com.example.alerting.shared;

/**
 * The stable, machine-readable error codes of the API.
 *
 * <p>Clients branch on these codes, never on the human-readable messages, which may be reworded at any
 * time. A code is therefore part of the API contract: add new ones freely, but never rename or reuse one.
 * Every code appears as the {@code code} field of an {@code application/problem+json} response.
 */
public final class ErrorCodes {

    /** The request body parsed but one or more fields are invalid; the response lists them. */
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    /** The request body could not be read: malformed JSON, an unknown field, or a value of the wrong type. */
    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    /** The caller could not be identified. */
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";

    /** The caller is known but is not allowed to do this. */
    public static final String FORBIDDEN = "FORBIDDEN";

    /** No such resource or path. */
    public static final String NOT_FOUND = "NOT_FOUND";

    /** The path exists but not for this HTTP method. */
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

    /** The request body is not in a supported format; the API speaks JSON. */
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";

    /** The request body exceeds the size limit. */
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";

    /** An event named a category that does not exist or is disabled. */
    public static final String CATEGORY_INVALID = "CATEGORY_INVALID";

    /** An event named a source that does not exist. */
    public static final String SOURCE_INVALID = "SOURCE_INVALID";

    /** An event named a source that an admin has disabled. */
    public static final String SOURCE_DISABLED = "SOURCE_DISABLED";

    /** A dependency such as the database is temporarily unavailable; nothing was changed, retrying is safe. */
    public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    /** An unexpected failure on our side. The response carries no internal detail. */
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private ErrorCodes() {
    }
}
