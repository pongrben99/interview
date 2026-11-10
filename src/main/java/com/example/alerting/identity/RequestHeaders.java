package com.example.alerting.identity;

/**
 * Read access to the headers of the current request, without tying the identity module to the servlet API.
 *
 * <p>The web layer passes {@code request::getHeader}.
 */
@FunctionalInterface
public interface RequestHeaders {

    /**
     * Returns the value of a header.
     *
     * @param name the header name, case-insensitive
     * @return the first value, or {@code null} if the request has no such header
     */
    String first(String name);
}
