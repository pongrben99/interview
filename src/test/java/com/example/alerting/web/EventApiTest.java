package com.example.alerting.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.alerting.event.Event;
import com.example.alerting.event.EventProcessingStatus;
import com.example.alerting.event.EventRepository;
import com.example.alerting.support.ApiTest;
import com.example.alerting.support.TestEvents;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * The push API, end to end: {@code POST /api/v1/events} with real filters, identity, validation, database
 * and error rendering (docs/02, sections 6.5 and 9). Requirement R-04: "authenticated push API; publishing
 * the same event twice is harmless".
 */
@ApiTest
class EventApiTest {

    private static final String ADMIN = "admin@example.com";
    private static final String ALICE = "alice@example.com";

    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository events;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        events.deleteAllInBatch();
        jdbc.update("UPDATE users SET active = TRUE");
    }

    // ---------------------------------------------------------------- success

    @Test
    void publishingANewEventReturns201AndStoresIt() throws Exception {
        Map<String, Object> body = TestEvents.quake();

        publish(ADMIN, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.duplicate").value(false));

        Event stored = onlyEvent();
        assertThat(stored.getExternalId()).isEqualTo(body.get("externalId"));
        assertThat(stored.getCategory()).isEqualTo("DISASTER");
        assertThat(stored.getRegion()).isEqualTo("TW");
        assertThat(stored.getProcessingStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
    }

    @Test
    void theSeverityIsDerivedFromTheAttributesWhenNotSupplied() throws Exception {
        publish(ADMIN, TestEvents.quake()).andExpect(status().isCreated());

        assertThat(onlyEvent().getSeverity()).isEqualTo(4); // magnitude 6.4
    }

    @Test
    void aSuppliedSeverityWinsOverTheDerivedOne() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("severity", 2);

        publish(ADMIN, body).andExpect(status().isCreated());

        assertThat(onlyEvent().getSeverity()).isEqualTo(2);
    }

    @Test
    void aMarketMoveGetsItsDerivedAttributes() throws Exception {
        publish(ADMIN, TestEvents.marketMove()).andExpect(status().isCreated());

        Event stored = onlyEvent();
        assertThat(stored.getSeverity()).isEqualTo(4); // 6.3 percent
        assertThat(stored.getAttributes()).contains("\"moveMagnitudePct\":6.3").contains("\"direction\":\"DOWN\"");
    }

    @Test
    void theDefaultSourceIsManual() throws Exception {
        Map<String, Object> body = TestEvents.marketMove(); // names no source
        publish(ADMIN, body).andExpect(status().isCreated());

        assertThat(events.findAll()).hasSize(1);
    }

    @Test
    void headerNameAndEmailCaseDoNotMatter() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("x-user-id", "  ADMIN@Example.COM ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(TestEvents.quake())))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------ idempotency

    @Test
    void publishingTheSameEventTwiceReturns200AndTheSameId() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        String first = publish(ADMIN, body).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        publish(ADMIN, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.id").value(json.readTree(first).get("id").asLong()));

        assertThat(events.findAll()).hasSize(1);
    }

    @Test
    void eventsWithoutAnExternalIdAreIdempotentByContent() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.remove("externalId");

        publish(ADMIN, body).andExpect(status().isCreated());
        publish(ADMIN, body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        Event stored = onlyEvent();
        assertThat(stored.getExternalId()).startsWith("h:").hasSize(66);
    }

    @Test
    void anotherEventWithoutAnExternalIdIsNotADuplicate() throws Exception {
        Map<String, Object> first = TestEvents.quake();
        first.remove("externalId");
        Map<String, Object> second = TestEvents.quake();
        second.remove("externalId");
        second.put("title", "A different earthquake");

        publish(ADMIN, first).andExpect(status().isCreated());
        publish(ADMIN, second).andExpect(status().isCreated());

        assertThat(events.findAll()).hasSize(2);
    }

    // ------------------------------------------------------------- validation

    @Test
    void everyInvalidFieldIsReportedTogether() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("title", "   ");
        body.put("severity", 9);
        body.put("url", "ftp://example.com/file");
        body.put("occurredAt", Instant.now().plusSeconds(3600).toString());

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.errors.length()").value(4))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("severity")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("url")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("occurredAt")));
        assertThat(events.count()).isZero();
    }

    @Test
    void aSeverityThatCannotBeDerivedIsAFieldError() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        TestEvents.attributesOf(body).clear(); // no magnitude

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("severity"))
                .andExpect(jsonPath("$.errors[0].message").value(containsString("magnitude")));
    }

    @Test
    void theRequiredFieldsAreReportedWhenMissing() throws Exception {
        publish(ADMIN, Map.of("source", "manual"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("category")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("title")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("occurredAt")))
                .andExpect(jsonPath("$.errors[?(@.field == 'occurredAt')].message").value(hasItem("is required")));
    }

    @Test
    void aMalformedOccurredAtIsAFieldError() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("occurredAt", "yesterday");

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("occurredAt"));
    }

    @Test
    void aNumericOccurredAtIsRejectedInsteadOfGuessingSecondsOrMilliseconds() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("occurredAt", 1_731_225_120L);

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("occurredAt"));
    }

    @Test
    void anUnknownFieldIsRejectedSoTyposDoNotPassSilently() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("titel", "typo");

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("titel"))
                .andExpect(jsonPath("$.errors[0].message").value("is not a known field"));
    }

    @Test
    void valuesOfTheWrongTypeAreRejectedNotCoerced() throws Exception {
        for (Object badSeverity : List.of("high", "4", 4.5, true)) {
            Map<String, Object> body = TestEvents.quake();
            body.put("severity", badSeverity);

            publish(ADMIN, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("severity"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be a whole number"));
        }
        Map<String, Object> numericTitle = TestEvents.quake();
        numericTitle.put("title", 12345);
        publish(ADMIN, numericTitle)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("must be text"));
    }

    @Test
    void attributesMustBeAnObjectOfScalars() throws Exception {
        Map<String, Object> notAnObject = TestEvents.quake();
        notAnObject.put("attributes", "magnitude=6.4");
        publish(ADMIN, notAnObject)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attributes"))
                .andExpect(jsonPath("$.errors[0].message").value("must be an object"));

        Map<String, Object> nested = TestEvents.quake();
        TestEvents.attributesOf(nested).put("nested", Map.of("a", 1));
        publish(ADMIN, nested)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attributes.nested"));
    }

    @Test
    void malformedJsonAndAnEmptyBodyAreMalformedRequests() throws Exception {
        for (String body : List.of("{ not json", "", "[]")) {
            mockMvc.perform(post("/api/v1/events").header("X-User-Id", ADMIN)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }

    // ----------------------------------------------------- source and category

    @Test
    void anUnknownCategoryIsCategoryInvalid() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("category", "SPORTS");

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID"))
                .andExpect(jsonPath("$.detail").value(containsString("SPORTS")));
    }

    @Test
    void aCategoryCodeMustMatchExactlyIncludingCase() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("category", "disaster");

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID"));
    }

    @Test
    void anUnknownSourceIsSourceInvalid() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("source", "nobody");

        publish(ADMIN, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOURCE_INVALID"));
    }

    @Test
    void aDisabledSourceIsAConflict() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("source", "usgs-earthquakes"); // seeded as disabled

        publish(ADMIN, body)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SOURCE_DISABLED"));
        assertThat(events.count()).isZero();
    }

    // ----------------------------------------------------- identity and roles

    @Test
    void aRequestWithoutIdentityIs401() throws Exception {
        publish(null, TestEvents.quake())
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
        assertThat(events.count()).isZero();
    }

    @Test
    void anUnknownBlankOrInactiveUserIs401WithTheSameAnswer() throws Exception {
        jdbc.update("UPDATE users SET active = FALSE WHERE email = ?", ALICE);
        for (String identity : List.of("nobody@example.com", "   ", ALICE, "a".repeat(400))) {
            publish(identity, TestEvents.quake())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.detail").value("Authentication is required."));
        }
    }

    @Test
    void anOrdinaryUserMayNotPublish() throws Exception {
        publish(ALICE, TestEvents.quake())
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.detail").value(containsString("ADMIN")));
        assertThat(events.count()).isZero();
    }

    // ----------------------------------------------------------- HTTP details

    @Test
    void aNonJsonBodyIs415() throws Exception {
        mockMvc.perform(post("/api/v1/events").header("X-User-Id", ADMIN)
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anOversizedBodyIs413() throws Exception {
        String huge = json.writeValueAsString(Map.of("title", "x".repeat(70_000)));

        mockMvc.perform(post("/api/v1/events").header("X-User-Id", ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(huge))
                .andExpect(status().is(413))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void aBodyJustUnderTheLimitIsProcessed() throws Exception {
        Map<String, Object> body = TestEvents.quake();
        body.put("summary", "s".repeat(1900)); // well under 64 KB, and under the 2000 character field limit

        publish(ADMIN, body).andExpect(status().isCreated());
    }

    @Test
    void theWrongMethodIs405() throws Exception {
        mockMvc.perform(get("/api/v1/events").header("X-User-Id", ADMIN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void anUnknownPathIs404ButOnlyForIdentifiedCallers() throws Exception {
        mockMvc.perform(get("/api/v1/nothing-here").header("X-User-Id", ADMIN))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void errorResponsesCarryTheRfc9457Fields() throws Exception {
        publish(ADMIN, Map.of("category", "SPORTS", "title", "x", "severity", 3, "occurredAt", Instant.now().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(startsWith("urn:alerting:problem:")))
                .andExpect(jsonPath("$.title").isString())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.code").value("CATEGORY_INVALID"))
                .andExpect(header().doesNotExist("X-Powered-By"));
    }

    // ----------------------------------------------------------------- helpers

    private ResultActions publish(String asUser, Map<String, Object> body) throws Exception {
        return publish(asUser, json.writeValueAsString(body));
    }

    private ResultActions publish(String asUser, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (asUser != null) {
            request.header("X-User-Id", asUser);
        }
        return mockMvc.perform(request);
    }

    private Event onlyEvent() {
        List<Event> all = events.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }
}
