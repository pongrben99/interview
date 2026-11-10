package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.alerting.event.EventIngestionService;
import com.example.alerting.event.EventProcessingStatus;
import com.example.alerting.event.IngestResult;
import com.example.alerting.event.NewEvent;
import com.example.alerting.shared.DomainException;
import com.example.alerting.support.ApiTest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * How failures that no real request can trigger on demand are rendered: an unavailable database, a surprise
 * exception, each kind of business failure. The ingestion service is replaced by a mock that throws, so the
 * test controls exactly what goes wrong. It also checks how a request body becomes an ingestion command.
 */
@ApiTest
class ProblemHandlingApiTest {

    private static final String BODY = "{\"category\":\"NEWS\",\"title\":\"x\",\"occurredAt\":\"2026-11-10T08:00:00Z\"}";

    @MockitoBean
    private EventIngestionService ingestion;

    @Autowired
    private MockMvc mockMvc;

    // ------------------------------------------------------------------ outages

    @Test
    void anUnavailableDatabaseIs503WithRetryAfterAndNoInternals() throws Exception {
        when(ingestion.ingest(any())).thenThrow(new DataAccessResourceFailureException(
                "Unable to acquire JDBC Connection jdbc:postgresql://db.internal:5432/alerting password=hunter2"));

        publish()
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail").value(containsString("safe to retry")))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("hunter2"))));
    }

    @Test
    void aDatabaseThatCannotStartATransactionIs503() throws Exception {
        when(ingestion.ingest(any())).thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager"));

        publish().andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"));
    }

    @Test
    void aTransientDatabaseFailureIs503() throws Exception {
        when(ingestion.ingest(any())).thenThrow(new QueryTimeoutException("statement timed out"));

        publish().andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    // ------------------------------------------------------------------ surprises

    @Test
    void anUnexpectedFailureIs500AndRevealsNothing() throws Exception {
        when(ingestion.ingest(any())).thenThrow(new IllegalStateException("password=hunter2 in com.secret.InternalClass"));

        publish()
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("com.secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    // ------------------------------------------------------------------ business failures

    @Test
    void eachKindOfBusinessFailureMapsToItsOwnStatusAndKeepsItsCode() throws Exception {
        expectFailure(DomainException.Kind.INVALID, 400);
        expectFailure(DomainException.Kind.UNAUTHENTICATED, 401);
        expectFailure(DomainException.Kind.FORBIDDEN, 403);
        expectFailure(DomainException.Kind.CONFLICT, 409);
    }

    private void expectFailure(DomainException.Kind kind, int expectedStatus) throws Exception {
        // doThrow, because the mock was already stubbed by the previous kind and `when(...)` would call it.
        doThrow(new DomainException(kind, "SOME_CODE", "Something specific.")).when(ingestion).ingest(any());

        publish()
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SOME_CODE"))
                .andExpect(jsonPath("$.detail").value("Something specific."))
                .andExpect(jsonPath("$.status").value(expectedStatus));
    }

    // ------------------------------------------------------------------ request to command

    @Test
    void aNewEventIs201AndADuplicateIs200() throws Exception {
        when(ingestion.ingest(any()))
                .thenReturn(new IngestResult(5, EventProcessingStatus.RECEIVED, false))
                .thenReturn(new IngestResult(5, EventProcessingStatus.MATCHED, true));

        publish().andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.duplicate").value(false));
        publish().andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void everyRequestFieldReachesTheIngestionCommand() throws Exception {
        when(ingestion.ingest(any())).thenReturn(new IngestResult(1, EventProcessingStatus.RECEIVED, false));
        String body = """
                {"source":"manual","externalId":"demo:1","category":"DISASTER","severity":4,
                 "title":"M 6.4","summary":"Shallow","url":"https://example.com/q","region":"TW",
                 "occurredAt":"2026-11-10T08:12:00Z","attributes":{"magnitude":6.4,"depthKm":10,"tsunami":false,"place":"Hualien"}}
                """;

        mockMvc.perform(post("/api/v1/events").header("X-User-Id", "admin@example.com")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());

        ArgumentCaptor<NewEvent> command = ArgumentCaptor.forClass(NewEvent.class);
        verify(ingestion).ingest(command.capture());
        NewEvent event = command.getValue();
        assertThat(event.source()).isEqualTo("manual");
        assertThat(event.externalId()).isEqualTo("demo:1");
        assertThat(event.category()).isEqualTo("DISASTER");
        assertThat(event.severity()).isEqualTo(4);
        assertThat(event.title()).isEqualTo("M 6.4");
        assertThat(event.summary()).isEqualTo("Shallow");
        assertThat(event.url()).isEqualTo("https://example.com/q");
        assertThat(event.region()).isEqualTo("TW");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-10T08:12:00Z"));
        assertThat(event.attributes()).isEqualTo(Map.of("magnitude", 6.4, "depthKm", 10, "tsunami", false, "place", "Hualien"));
    }

    @Test
    void anOccurredAtWithAnOffsetIsReadAsTheSameInstant() throws Exception {
        when(ingestion.ingest(any())).thenReturn(new IngestResult(1, EventProcessingStatus.RECEIVED, false));

        mockMvc.perform(post("/api/v1/events").header("X-User-Id", "admin@example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"NEWS\",\"title\":\"x\",\"occurredAt\":\" 2026-11-10T09:12:00+01:00 \"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<NewEvent> command = ArgumentCaptor.forClass(NewEvent.class);
        verify(ingestion).ingest(command.capture());
        assertThat(command.getValue().occurredAt()).isEqualTo(Instant.parse("2026-11-10T08:12:00Z"));
    }

    @Test
    void aMissingOptionalFieldArrivesAsNullAndMissingAttributesAsEmpty() throws Exception {
        when(ingestion.ingest(any())).thenReturn(new IngestResult(1, EventProcessingStatus.RECEIVED, false));

        publish().andExpect(status().isCreated());

        ArgumentCaptor<NewEvent> command = ArgumentCaptor.forClass(NewEvent.class);
        verify(ingestion).ingest(command.capture());
        assertThat(command.getValue().source()).isNull();
        assertThat(command.getValue().severity()).isNull();
        assertThat(command.getValue().attributes()).isEmpty();
    }

    private ResultActions publish() throws Exception {
        return mockMvc.perform(post("/api/v1/events").header("X-User-Id", "admin@example.com")
                .contentType(MediaType.APPLICATION_JSON).content(BODY));
    }
}
