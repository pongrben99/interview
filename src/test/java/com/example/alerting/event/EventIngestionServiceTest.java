package com.example.alerting.event;

import static com.example.alerting.support.NewEventBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.shared.DomainException;
import com.example.alerting.shared.ValidationException;
import com.example.alerting.support.MutableClock;
import com.example.alerting.support.ServiceTest;
import com.example.alerting.support.TestEvents;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Event ingestion against the real database: storing, deduplicating, rejecting, announcing, and the
 * concurrent race that the unique key must settle (requirement R-04: "publishing the same event twice is
 * harmless"; plan R-04: "50-thread duplicate race").
 */
@ServiceTest
@Import(EventIngestionServiceTest.TestBeans.class)
class EventIngestionServiceTest {

    private static final MutableClock CLOCK = new MutableClock(TestEvents.NOW);
    private static final AcceptedRecorder RECORDER = new AcceptedRecorder();

    /** Collects the {@link EventAccepted} announcements, from any thread. */
    static class AcceptedRecorder {

        final List<EventAccepted> accepted = new CopyOnWriteArrayList<>();

        @EventListener
        void on(EventAccepted event) {
            accepted.add(event);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

        @Bean
        AcceptedRecorder acceptedRecorder() {
            return RECORDER;
        }
    }

    @Autowired
    private EventIngestionService ingestion;

    @Autowired
    private EventRepository events;

    @Autowired
    private EventSourceRepository sources;

    @Autowired
    private EventCategoryRepository categories;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void reset() {
        events.deleteAllInBatch();
        RECORDER.accepted.clear();
        CLOCK.set(TestEvents.NOW);
    }

    @AfterEach
    void cleanUp() {
        events.deleteAllInBatch();
        categories.deleteById("SPORTS");
    }

    // ------------------------------------------------------------------ storing

    @Test
    void aNewEventIsStoredNormalizedAndAnnouncedOnce() {
        IngestResult result = ingestion.ingest(anEvent().externalId("  e-1 ").title("  M 6.4 \u0000quake  ")
                .region("tw").summary(" Shallow ").url("https://example.com/x").build());

        assertThat(result.duplicate()).isFalse();
        assertThat(result.status()).isEqualTo(EventProcessingStatus.RECEIVED);
        Event stored = events.findById(result.eventId()).orElseThrow();
        assertThat(stored.getSourceId()).isEqualTo(sourceId("manual"));
        assertThat(stored.getExternalId()).isEqualTo("e-1");
        assertThat(stored.getCategory()).isEqualTo("DISASTER");
        assertThat(stored.getSeverity()).isEqualTo(4);
        assertThat(stored.getTitle()).isEqualTo("M 6.4 quake");
        assertThat(stored.getSummary()).isEqualTo("Shallow");
        assertThat(stored.getUrl()).isEqualTo("https://example.com/x");
        assertThat(stored.getRegion()).isEqualTo("TW");
        assertThat(stored.getOccurredAt()).isEqualTo(TestEvents.NOW.minusSeconds(60));
        assertThat(stored.getReceivedAt()).isEqualTo(TestEvents.NOW);
        assertThat(stored.getAttributes()).isEqualTo("{\"magnitude\":6.4}");
        assertThat(stored.getProcessingStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
        assertThat(RECORDER.accepted).containsExactly(new EventAccepted(result.eventId()));
    }

    @Test
    void theDefaultSourceIsManual() {
        IngestResult result = ingestion.ingest(anEvent().source(null).externalId("e-1").build());

        assertThat(events.findById(result.eventId()).orElseThrow().getSourceId()).isEqualTo(sourceId("manual"));
    }

    @Test
    void derivedAttributesAreStoredWithTheEvent() {
        IngestResult result = ingestion.ingest(anEvent().category("MARKET").externalId("m-1")
                .attributes(Map.of("changePct", -6.3)).build());

        Event stored = events.findById(result.eventId()).orElseThrow();
        assertThat(stored.getSeverity()).isEqualTo(4);
        assertThat(stored.getAttributes()).contains("\"moveMagnitudePct\":6.3").contains("\"direction\":\"DOWN\"");
    }

    @Test
    void anEventUpToFiveMinutesInTheFutureIsAcceptedButNotMore() {
        ingestion.ingest(anEvent().externalId("ok").occurredAt(TestEvents.NOW.plusSeconds(300)).build());

        assertThatThrownBy(() -> ingestion.ingest(anEvent().externalId("late").occurredAt(TestEvents.NOW.plusSeconds(301)).build()))
                .isInstanceOf(ValidationException.class);
        assertThat(events.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ idempotency

    @Test
    void aRepeatSubmissionIsADuplicateThatChangesAndAnnouncesNothing() {
        IngestResult first = ingestion.ingest(anEvent().externalId("e-1").build());
        IngestResult second = ingestion.ingest(anEvent().externalId("e-1").build());

        assertThat(first.duplicate()).isFalse();
        assertThat(second.duplicate()).isTrue();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(events.count()).isEqualTo(1);
        assertThat(RECORDER.accepted).hasSize(1);
    }

    @Test
    void storedEventsAreImmutableALaterSubmissionWithTheSameIdIsIgnored() {
        IngestResult first = ingestion.ingest(anEvent().externalId("e-1").title("Original").build());

        IngestResult second = ingestion.ingest(anEvent().externalId("e-1").title("Revised").severity(1).build());

        assertThat(second.duplicate()).isTrue();
        Event stored = events.findById(first.eventId()).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Original");
        assertThat(stored.getSeverity()).isEqualTo(4);
    }

    @Test
    void aDuplicateReportsTheCurrentStatusOfTheStoredEvent() {
        IngestResult first = ingestion.ingest(anEvent().externalId("e-1").build());

        assertThat(ingestion.ingest(anEvent().externalId("e-1").build()).status()).isEqualTo(first.status());
    }

    @Test
    void theSameExternalIdFromAnotherSourceIsAnotherEvent() {
        IngestResult manual = ingestion.ingest(anEvent().source("manual").externalId("same").build());
        IngestResult simulator = ingestion.ingest(anEvent().source("simulator").externalId("same").build());

        assertThat(simulator.duplicate()).isFalse();
        assertThat(simulator.eventId()).isNotEqualTo(manual.eventId());
        assertThat(events.count()).isEqualTo(2);
    }

    @Test
    void withoutAnExternalIdTheContentDecidesWhetherItIsADuplicate() {
        IngestResult first = ingestion.ingest(anEvent().externalId(null).build());
        IngestResult same = ingestion.ingest(anEvent().externalId(null).build());
        IngestResult other = ingestion.ingest(anEvent().externalId(null).title("Another quake").build());

        assertThat(same.duplicate()).isTrue();
        assertThat(same.eventId()).isEqualTo(first.eventId());
        assertThat(other.duplicate()).isFalse();
        assertThat(events.findById(first.eventId()).orElseThrow().getExternalId()).startsWith("h:");
    }

    @Test
    void ingestionDoesNotJoinACallersTransactionSoItsOwnFailureCannotPoisonIt() {
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        List<IngestResult> results = new ArrayList<>();

        outer.executeWithoutResult(status -> {
            results.add(ingestion.ingest(anEvent().externalId("e-1").build()));
            results.add(ingestion.ingest(anEvent().externalId("e-1").build()));
            status.setRollbackOnly();
        });

        assertThat(results).extracting(IngestResult::duplicate).containsExactly(false, true);
        assertThat(events.count()).as("the insert committed on its own even though the caller rolled back").isEqualTo(1);
    }

    // ------------------------------------------------------------------ rejection

    @Test
    void anUnknownSourceIsRejectedAsInvalid() {
        assertRejected(anEvent().source("nobody").build(), "SOURCE_INVALID", DomainException.Kind.INVALID);
    }

    @Test
    void aDisabledSourceIsRejectedAsAConflict() {
        assertRejected(anEvent().source("usgs-earthquakes").build(), "SOURCE_DISABLED", DomainException.Kind.CONFLICT);
    }

    @Test
    void anUnknownCategoryIsRejectedAsInvalid() {
        assertRejected(anEvent().category("SPORTS").severity(3).build(), "CATEGORY_INVALID", DomainException.Kind.INVALID);
    }

    @Test
    void aDisabledCategoryIsRejectedAsInvalid() {
        categories.save(new EventCategory("SPORTS", "Sports", null, false));

        assertRejected(anEvent().category("SPORTS").severity(3).build(), "CATEGORY_INVALID", DomainException.Kind.INVALID);
    }

    @Test
    void anEnabledCustomCategoryWorksWhenTheSeverityIsSupplied() {
        categories.save(new EventCategory("SPORTS", "Sports", null, true));

        IngestResult result = ingestion.ingest(anEvent().category("SPORTS").severity(3).noAttributes().externalId("s-1").build());

        assertThat(events.findById(result.eventId()).orElseThrow().getSeverity()).isEqualTo(3);
    }

    @Test
    void aCategoryMatchesExactlyIncludingCase() {
        assertRejected(anEvent().category("disaster").build(), "CATEGORY_INVALID", DomainException.Kind.INVALID);
    }

    @Test
    void aPayloadThatBreaksTheFieldRulesStoresNothing() {
        assertThatThrownBy(() -> ingestion.ingest(anEvent().title(" ").severity(9).build()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors()).hasSize(2));

        assertNothingStoredOrAnnounced();
    }

    @Test
    void aSeverityThatCannotBeDerivedStoresNothing() {
        assertThatThrownBy(() -> ingestion.ingest(anEvent().noAttributes().build()))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getErrors().get(0).field()).isEqualTo("severity"));

        assertNothingStoredOrAnnounced();
    }

    @Test
    void attributesThatOutgrowTheColumnOnceDerivedAttributesAreAddedAreRejected() {
        // {"note":"..."} is 11 characters of frame; leave too little room for the derived attributes.
        String filler = "x".repeat(AttributesCodec.MAX_LENGTH - 60);
        NewEvent nearlyFull = anEvent().category("MARKET").externalId("m-big")
                .attribute("changePct", -6.3).attribute("note", filler).build();

        assertThatThrownBy(() -> ingestion.ingest(nearlyFull))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getErrors().get(0).field()).isEqualTo("attributes"));

        assertNothingStoredOrAnnounced();
    }

    // ------------------------------------------------------------------ concurrency

    @RepeatedTest(5)
    void fiftyThreadsPublishingTheSameEventStoreExactlyOne() throws Exception {
        List<IngestResult> results = publishConcurrently(50, i -> anEvent().externalId("race-1").build());

        assertExactlyOneCreated(results);
    }

    @RepeatedTest(5)
    void fiftyThreadsPublishingTheSameContentWithoutAnIdStoreExactlyOne() throws Exception {
        List<IngestResult> results = publishConcurrently(50, i -> anEvent().externalId(null).build());

        assertExactlyOneCreated(results);
        assertThat(events.findAll().get(0).getExternalId()).startsWith("h:");
    }

    @Test
    void fiftyDifferentEventsPublishedAtOnceAreAllStored() throws Exception {
        List<IngestResult> results = publishConcurrently(50, i -> anEvent().externalId("distinct-" + i).build());

        assertThat(results).extracting(IngestResult::duplicate).containsOnly(false);
        Set<Long> ids = new HashSet<>();
        results.forEach(result -> ids.add(result.eventId()));
        assertThat(ids).hasSize(50);
        assertThat(events.count()).isEqualTo(50);
        assertThat(RECORDER.accepted).hasSize(50);
    }

    private void assertExactlyOneCreated(List<IngestResult> results) {
        assertThat(results.stream().filter(result -> !result.duplicate())).hasSize(1);
        assertThat(results.stream().filter(IngestResult::duplicate)).hasSize(results.size() - 1);
        assertThat(results.stream().map(IngestResult::eventId).distinct()).hasSize(1);
        assertThat(events.count()).isEqualTo(1);
        assertThat(RECORDER.accepted).hasSize(1);
    }

    private List<IngestResult> publishConcurrently(int threads, IntFunction<NewEvent> eventFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<IngestResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                NewEvent event = eventFor.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return ingestion.ingest(event);
                }));
            }
            ready.await();
            go.countDown();
            List<IngestResult> results = new ArrayList<>();
            for (Future<IngestResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ helpers

    private void assertRejected(NewEvent event, String expectedCode, DomainException.Kind expectedKind) {
        assertThatThrownBy(() -> ingestion.ingest(event))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getCode()).isEqualTo(expectedCode);
                    assertThat(e.getKind()).isEqualTo(expectedKind);
                });
        assertNothingStoredOrAnnounced();
    }

    private void assertNothingStoredOrAnnounced() {
        assertThat(events.count()).isZero();
        assertThat(RECORDER.accepted).isEmpty();
    }

    private long sourceId(String code) {
        return sources.findByCode(code).orElseThrow().getId();
    }
}
