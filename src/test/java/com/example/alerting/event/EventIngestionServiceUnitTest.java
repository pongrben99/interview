package com.example.alerting.event;

import static com.example.alerting.support.NewEventBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.alerting.support.MutableClock;
import com.example.alerting.support.TestEvents;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * The branches of {@link EventIngestionService} that are hard to provoke on demand against a real
 * database: losing a race, an unrelated constraint violation, a failing listener, an outage. The happy
 * paths and the real concurrent race are in {@link EventIngestionServiceTest}.
 */
class EventIngestionServiceUnitTest {

    private static final long SOURCE_ID = 11L;

    private final EventSourceRepository sources = mock(EventSourceRepository.class);
    private final EventCategoryRepository categories = mock(EventCategoryRepository.class);
    private final EventRepository events = mock(EventRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private EventIngestionService service;

    @BeforeEach
    void setUp() {
        EventSource manual = new EventSource("manual", "Manual", SourceType.PUSH, true, null, TestEvents.NOW);
        ReflectionTestUtils.setField(manual, "id", SOURCE_ID);
        when(sources.findByCode("manual")).thenReturn(Optional.of(manual));
        when(categories.findById("DISASTER"))
                .thenReturn(Optional.of(new EventCategory("DISASTER", "Disasters", null, true)));
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        EventNormalizer normalizer = new EventNormalizer(List.of(new DisasterSeverityNormalizer(List.of(4.0, 5.0, 6.0, 7.0))));
        service = new EventIngestionService(new EventValidator(), normalizer, new AttributesCodec(), sources,
                categories, events, publisher, new MutableClock(TestEvents.NOW), transactionManager);
    }

    @Test
    void losingTheRaceIsAnsweredWithTheEventThatWon() {
        Event winner = storedEvent(77L);
        when(events.findBySourceIdAndExternalId(SOURCE_ID, "e-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(events.saveAndFlush(any(Event.class))).thenThrow(new DataIntegrityViolationException("uq_events_source_external"));

        IngestResult result = service.ingest(anEvent().externalId("e-1").build());

        assertThat(result).isEqualTo(new IngestResult(77L, EventProcessingStatus.RECEIVED, true));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void aViolationThatIsNotADuplicateIsNotHiddenAsOne() {
        DataIntegrityViolationException violation = new DataIntegrityViolationException("fk_events_source");
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Event.class))).thenThrow(violation);

        assertThatThrownBy(() -> service.ingest(anEvent().externalId("e-1").build())).isSameAs(violation);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theInsertRunsInItsOwnNewTransaction() {
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Event.class))).thenReturn(storedEvent(5L));

        service.ingest(anEvent().externalId("e-1").build());

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(transactionManager).commit(any());
    }

    @Test
    void aFailingListenerDoesNotTurnACommittedIngestionIntoAnError() {
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Event.class))).thenReturn(storedEvent(5L));
        doThrow(new IllegalStateException("matcher is broken")).when(publisher).publishEvent(any(Object.class));

        IngestResult result = service.ingest(anEvent().externalId("e-1").build());

        assertThat(result).isEqualTo(new IngestResult(5L, EventProcessingStatus.RECEIVED, false));
    }

    @Test
    void theAcceptedEventIsAnnouncedWithItsId() {
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Event.class))).thenReturn(storedEvent(5L));

        service.ingest(anEvent().externalId("e-1").build());

        verify(publisher).publishEvent(new EventAccepted(5L));
    }

    @Test
    void anOutageWhileLookingUpPropagatesAndStoresNothing() {
        DataAccessResourceFailureException outage = new DataAccessResourceFailureException("connection refused");
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenThrow(outage);

        assertThatThrownBy(() -> service.ingest(anEvent().externalId("e-1").build())).isSameAs(outage);

        verify(events, never()).saveAndFlush(any(Event.class));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void anOutageWhileInsertingPropagatesBecauseNothingWasAccepted() {
        DataAccessResourceFailureException outage = new DataAccessResourceFailureException("connection lost");
        when(events.findBySourceIdAndExternalId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(events.saveAndFlush(any(Event.class))).thenThrow(outage);

        assertThatThrownBy(() -> service.ingest(anEvent().externalId("e-1").build())).isSameAs(outage);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    private static Event storedEvent(long id) {
        Event event = new Event(SOURCE_ID, "e-1", "DISASTER", 4, "M 6.4 earthquake near Hualien, Taiwan", null, null,
                "TW", TestEvents.NOW.minusSeconds(60), TestEvents.NOW, "{\"magnitude\":6.4}");
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }
}
