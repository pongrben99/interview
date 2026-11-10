package com.example.alerting.web;

import java.io.IOException;

/**
 * A request body is larger than the configured limit.
 *
 * <p>It is an {@link IOException} because it is thrown from inside the input stream while the body is read;
 * the problem handler turns it into a {@code 413} response whether it arrives directly or wrapped by the
 * JSON reader.
 */
public class RequestBodyTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    private final long maxBytes;

    /**
     * Creates the exception.
     *
     * @param maxBytes the limit that was exceeded
     */
    public RequestBodyTooLargeException(long maxBytes) {
        super("The request body is larger than " + maxBytes + " bytes");
        this.maxBytes = maxBytes;
    }

    /**
     * The limit that was exceeded.
     *
     * @return the maximum body size in bytes
     */
    public long getMaxBytes() {
        return maxBytes;
    }
}
