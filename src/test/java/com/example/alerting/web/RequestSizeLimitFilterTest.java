package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/** The filter that refuses oversized {@code /api/} request bodies, with and without a declared length. */
class RequestSizeLimitFilterTest {

    private static final int LIMIT = 100;

    private final List<Exception> resolved = new ArrayList<>();
    private boolean resolverHandlesIt = true;
    private final HandlerExceptionResolver resolver = (request, response, handler, exception) -> {
        resolved.add(exception);
        return resolverHandlesIt ? new ModelAndView() : null;
    };
    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new HttpProperties(LIMIT), resolver);

    /** A request whose length is not declared, like a chunked upload. */
    private static MockHttpServletRequest chunked(String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        return request;
    }

    private static MockHttpServletRequest declared(String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContent(body);
        return request;
    }

    @Test
    void aBodyWithinTheLimitPassesThroughAndCanBeRead() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = declared("/api/v1/events", new byte[LIMIT]);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest passedOn = (HttpServletRequest) chain.getRequest();
        assertThat(passedOn).isNotNull().isNotSameAs(request);
        assertThat(passedOn.getInputStream().readAllBytes()).hasSize(LIMIT);
        assertThat(resolved).isEmpty();
    }

    @Test
    void aDeclaredLengthOverTheLimitIsRefusedWithoutReadingAnything() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(declared("/api/v1/events", new byte[LIMIT + 1]), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).as("the chain must not run").isNull();
        assertThat(resolved).singleElement().isInstanceOfSatisfying(RequestBodyTooLargeException.class,
                e -> assertThat(e.getMaxBytes()).isEqualTo(LIMIT));
    }

    @Test
    void whenNothingHandlesTheRefusalTheResponseStillSays413() throws Exception {
        resolverHandlesIt = false;
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(declared("/api/v1/events", new byte[LIMIT + 1]), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    void anUndeclaredLengthIsCutOffWhileTheBodyIsRead() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = chunked("/api/v1/events", new byte[LIMIT + 50]);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest passedOn = (HttpServletRequest) chain.getRequest();
        assertThat(passedOn).isNotNull();
        assertThatThrownBy(() -> passedOn.getInputStream().readAllBytes()).isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void anUndeclaredLengthWithinTheLimitIsFine() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(chunked("/api/v1/events", new byte[LIMIT]), new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes()).hasSize(LIMIT);
    }

    @Test
    void theReaderIsLimitedToo() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = chunked("/api/v1/events", "x".repeat(LIMIT + 1).getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        BufferedReader reader = ((HttpServletRequest) chain.getRequest()).getReader();
        assertThatThrownBy(() -> reader.lines().count()).hasCauseInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void theReaderHonoursTheDeclaredCharacterEncoding() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = chunked("/api/v1/events", "ünï".getBytes(StandardCharsets.ISO_8859_1));
        request.setCharacterEncoding("ISO-8859-1");

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getReader().readLine()).isEqualTo("ünï");
    }

    @Test
    void theReaderDefaultsToUtf8WhenNoEncodingIsDeclared() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = chunked("/api/v1/events", "ünï".getBytes(StandardCharsets.UTF_8));
        request.setCharacterEncoding((String) null);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getReader().readLine()).isEqualTo("ünï");
    }

    @Test
    void theBodyStreamIsTheSameObjectOnEveryCall() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(declared("/api/v1/events", new byte[10]), new MockHttpServletResponse(), chain);

        HttpServletRequest passedOn = (HttpServletRequest) chain.getRequest();
        assertThat(passedOn.getInputStream()).isSameAs(passedOn.getInputStream());
    }

    @Test
    void requestsOutsideTheApiAreNotChecked() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = declared("/index.html", new byte[LIMIT * 10]);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(resolved).isEmpty();
    }

    @Test
    void aRequestWithoutABodyPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/events"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(resolved).isEmpty();
    }

    @Test
    void anIoErrorFromTheReaderIsNotSwallowed() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = chunked("/api/v1/events", new byte[LIMIT + 1]);
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        BufferedReader reader = ((HttpServletRequest) chain.getRequest()).getReader();

        assertThatThrownBy(() -> {
            while (reader.read() != -1) {
                // keep reading until the limit is crossed
            }
        }).isInstanceOf(IOException.class);
    }
}
