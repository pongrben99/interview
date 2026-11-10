package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.event.EventRepository;
import com.example.alerting.support.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

/**
 * The request size limit over a real socket, where {@code MockMvc} cannot go: a body sent with chunked
 * transfer encoding has no declared length, so only the counting stream can stop it.
 */
@IntegrationTest
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
class ChunkedBodyLimitTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private EventRepository events;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void cleanUp() {
        events.deleteAllInBatch();
    }

    @Test
    void aChunkedBodyOverTheLimitIsRefusedWith413() throws Exception {
        byte[] body = ("{\"title\":\"" + "x".repeat(70_000) + "\"}").getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send(chunked(body));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void aBodyWithADeclaredLengthOverTheLimitIsRefusedWith413() throws Exception {
        byte[] body = ("{\"title\":\"" + "x".repeat(70_000) + "\"}").getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send(builder().POST(HttpRequest.BodyPublishers.ofByteArray(body)).build());

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("\"code\":\"PAYLOAD_TOO_LARGE\"");
    }

    @Test
    void aNormalChunkedRequestIsStillProcessed() throws Exception {
        byte[] body = ("{\"category\":\"NEWS\",\"title\":\"Chunked headline\",\"occurredAt\":\""
                + Instant.now().minusSeconds(30) + "\"}").getBytes(StandardCharsets.UTF_8);

        HttpResponse<String> response = send(chunked(body));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"duplicate\":false");
        assertThat(events.count()).isEqualTo(1);
    }

    private HttpRequest chunked(byte[] body) {
        // A body publisher without a known length makes the client use chunked transfer encoding.
        return builder().POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))).build();
    }

    private HttpRequest.Builder builder() {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/events"))
                .header("Content-Type", "application/json")
                .header("X-User-Id", "admin@example.com");
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
