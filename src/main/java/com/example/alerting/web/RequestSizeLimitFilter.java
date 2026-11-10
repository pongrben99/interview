package com.example.alerting.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Rejects oversized API request bodies with {@code 413} before the application spends time on them
 * (docs/02, section 6.5: "Body too large: 413").
 *
 * <p>A declared {@code Content-Length} above the limit is refused immediately. A body without a declared
 * length, or one that lies about it, is cut off while it is read ({@link LimitedServletInputStream}). The
 * refusal goes through the normal exception handling, so the client gets the same
 * {@code application/problem+json} as for every other error. Only {@code /api/} requests are checked.
 *
 * <p>It exists only in a servlet web application: contexts without a web layer (for example persistence
 * tests) have no MVC exception resolver to hand the refusal to.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final HandlerExceptionResolver exceptionResolver;

    /**
     * Creates the filter.
     *
     * @param properties holds the size limit
     * @param exceptionResolver the MVC exception resolver, which applies the problem+json handler
     */
    public RequestSizeLimitFilter(HttpProperties properties,
                                  @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.maxBytes = properties.maxRequestBodyBytes();
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            RequestBodyTooLargeException tooLarge = new RequestBodyTooLargeException(maxBytes);
            if (exceptionResolver.resolveException(request, response, null, tooLarge) == null) {
                response.sendError(HttpStatus.CONTENT_TOO_LARGE.value());
            }
            return;
        }
        chain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** Wraps the request so that its body stream enforces the limit. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private ServletInputStream limited;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (limited == null) {
                limited = new LimitedServletInputStream(super.getInputStream(), maxBytes);
            }
            return limited;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
