package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.alerting.shared.FieldError;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.core.exc.JacksonIOException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The reading of Jackson failures and the decoration of Spring's own errors, tested on the handler directly
 * with real Jackson exceptions. The end-to-end behavior is in {@link EventApiTest}.
 */
class ProblemExceptionHandlerTest {

    private record Inner(int count) {
    }

    private record Outer(List<Inner> items, Inner inner, boolean flag, Map<String, Object> attrs, String text,
                         Instant when, long big, short small, Integer boxedInt, Long boxedLong, Short boxedShort,
                         Boolean boxedFlag) {
    }

    private final ProblemExceptionHandler handler = new ProblemExceptionHandler();
    // Strict about unknown fields like the application (application.yml). Jackson 3 also rejects a missing
    // primitive by default; these tests are about the descriptions of wrong types, not about that.
    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();
    private final WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    // ------------------------------------------------------------------ JSON problems

    @Test
    void anUnknownNestedFieldIsNamedWithItsFullPath() {
        ProblemDetail problem = problemForJson("{\"inner\":{\"count\":1,\"bad\":2}}");

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(codeOf(problem)).isEqualTo("VALIDATION_FAILED");
        assertThat(errorsOf(problem)).containsExactly(new FieldError("inner.bad", "is not a known field"));
    }

    @Test
    void aWrongTypeInsideAListIsNamedWithItsIndex() {
        ProblemDetail problem = problemForJson("{\"items\":[{\"count\":1},{\"count\":\"x\"}]}");

        assertThat(errorsOf(problem)).containsExactly(new FieldError("items[1].count", "must be a whole number"));
    }

    @Test
    void theExpectedTypeIsDescribedInPlainWords() {
        assertThat(messageFor("{\"inner\":{\"count\":\"x\"}}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"big\":\"x\"}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"small\":\"x\"}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"boxedInt\":\"x\"}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"boxedLong\":\"x\"}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"boxedShort\":\"x\"}")).isEqualTo("must be a whole number");
        assertThat(messageFor("{\"flag\":\"maybe\"}")).isEqualTo("must be true or false");
        assertThat(messageFor("{\"boxedFlag\":\"maybe\"}")).isEqualTo("must be true or false");
        assertThat(messageFor("{\"text\":[1]}")).isEqualTo("must be text");
        assertThat(messageFor("{\"attrs\":\"x\"}")).isEqualTo("must be an object");
        assertThat(messageFor("{\"when\":\"yesterday\"}")).isEqualTo("must be a value of the right type");
    }

    @Test
    void aHostileFieldNameIsMadeSafeBeforeItIsEchoedBack() {
        ProblemDetail problem = problemForJson("{\"bad\\u0001name\":1}");

        assertThat(errorsOf(problem)).containsExactly(new FieldError("bad?name", "is not a known field"));
    }

    @Test
    void aVeryLongFieldNameIsShortened() {
        ProblemDetail problem = problemForJson("{\"" + "n".repeat(500) + "\":1}");

        assertThat(errorsOf(problem).get(0).field()).hasSizeLessThanOrEqualTo(100).endsWith("…");
    }

    @Test
    void brokenJsonAnEmptyBodyAndAWholeBodyOfTheWrongShapeAreMalformedRequests() {
        for (String json : List.of("{ not json", "", "[]", "\"text\"")) {
            ProblemDetail problem = problemForJson(json);

            assertThat(problem.getStatus()).as(json).isEqualTo(400);
            assertThat(codeOf(problem)).as(json).isEqualTo("MALFORMED_REQUEST");
            assertThat(problem.getProperties()).as(json).doesNotContainKey("errors");
        }
    }

    @Test
    void aBodyThatIsMissingEntirelyIsAMalformedRequest() {
        HttpMessageNotReadableException missing = new HttpMessageNotReadableException(
                "Required request body is missing", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(missing, new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request);

        assertThat(codeOf(problemOf(response))).isEqualTo("MALFORMED_REQUEST");
    }

    // ------------------------------------------------------------------ the size limit inside a read

    @Test
    void aSizeLimitHitWhileTheBodyIsReadIsA413EvenWhenWrappedByTheJsonReader() {
        Throwable wrapped = JacksonIOException.construct(new RequestBodyTooLargeException(65_536));

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("cannot read", wrapped, emptyMessage()), new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(codeOf(problemOf(response))).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(problemOf(response).getDetail()).contains("65536");
    }

    @Test
    void theSearchForTheCauseGivesUpOnAbsurdlyDeepChains() {
        Throwable chain = new RequestBodyTooLargeException(1);
        for (int i = 0; i < 12; i++) {
            chain = new RuntimeException("wrapper " + i, chain);
        }

        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("cannot read", chain, emptyMessage()), new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    // ------------------------------------------------------------------ Spring's own errors

    @Test
    void springErrorsGetTheSameProblemShapeWithAStableCode() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")), request);

        ProblemDetail problem = problemOf(response);
        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
        assertThat(codeOf(problem)).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(problem.getType()).isEqualTo(URI.create("urn:alerting:problem:method-not-allowed"));
    }

    @Test
    void unsupportedMediaTypesAreCodedAsSuch() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(codeOf(problemOf(response))).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void springErrorsOfEveryCommonStatusGetTheirOwnStableCode() throws Exception {
        ResponseEntity<Object> badParameter = handler.handleException(
                new MissingServletRequestParameterException("q", "String"), request);
        ResponseEntity<Object> tooLarge = handler.handleException(new MaxUploadSizeExceededException(100), request);
        ResponseEntity<Object> timeout = handler.handleException(new AsyncRequestTimeoutException(), request);

        assertThat(badParameter.getStatusCode().value()).isEqualTo(400);
        assertThat(codeOf(problemOf(badParameter))).isEqualTo("MALFORMED_REQUEST");
        assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
        assertThat(codeOf(problemOf(tooLarge))).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(timeout.getStatusCode().value()).isEqualTo(503);
        assertThat(codeOf(problemOf(timeout))).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void otherStatusesFallBackToACodeDerivedFromTheStatus() throws Exception {
        ResponseEntity<Object> response = handler.handleException(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(codeOf(problemOf(response))).isEqualTo("HTTP_406");
    }

    @Test
    void aProblemThatAlreadyHasACodeKeepsIt() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setProperty("code", "ALREADY_SET");
        problem.setType(URI.create("urn:custom"));

        ResponseEntity<Object> response = handler.handleExceptionInternal(new RuntimeException("x"), problem,
                new HttpHeaders(), HttpStatus.NOT_FOUND, request);

        assertThat(codeOf(problemOf(response))).isEqualTo("ALREADY_SET");
        assertThat(problemOf(response).getType()).isEqualTo(URI.create("urn:custom"));
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void aResponseThatIsNotAProblemIsLeftAlone() {
        ResponseEntity<Object> response = handler.handleExceptionInternal(new RuntimeException("x"), "plain text",
                new HttpHeaders(), HttpStatus.BAD_REQUEST, request);

        assertThat(response.getBody()).isEqualTo("plain text");
    }

    // ------------------------------------------------------------------ helpers

    private ProblemDetail problemForJson(String json) {
        Throwable failure = catchThrowable(() -> mapper.readValue(json, Outer.class));
        assertThat(failure).as("the JSON %s must fail to parse", json).isNotNull();
        ResponseEntity<Object> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("cannot read", failure, emptyMessage()), new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request);
        return problemOf(response);
    }

    private String messageFor(String json) {
        return errorsOf(problemForJson(json)).get(0).message();
    }

    private static HttpInputMessage emptyMessage() {
        return new MockHttpInputMessage(new byte[0]);
    }

    private static ProblemDetail problemOf(ResponseEntity<Object> response) {
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        return (ProblemDetail) response.getBody();
    }

    private static String codeOf(ProblemDetail problem) {
        return (String) problem.getProperties().get("code");
    }

    @SuppressWarnings("unchecked")
    private static List<FieldError> errorsOf(ProblemDetail problem) {
        return (List<FieldError>) problem.getProperties().get("errors");
    }
}
