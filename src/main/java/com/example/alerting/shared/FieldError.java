package com.example.alerting.shared;

import java.util.Objects;

/**
 * One problem with one input field, as listed in the {@code errors} array of a validation error response.
 *
 * @param field the offending field; nested values use a dot or an index, for example
 *              {@code attributes.depthKm}
 * @param message what is wrong and how to fix it, in plain English
 */
public record FieldError(String field, String message) {

    /**
     * Creates a field error.
     *
     * @throws NullPointerException if the field or the message is {@code null}
     */
    public FieldError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(message, "message");
    }
}
