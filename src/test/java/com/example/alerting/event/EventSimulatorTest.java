package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.ValidationException;
import com.example.alerting.support.MutableClock;
import com.example.alerting.support.TestEvents;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The simulator is deterministic and produces events the real pipeline accepts (R-05). */
class EventSimulatorTest {

    private static final Instant NOW = TestEvents.NOW;

    private final EventValidator validator = new EventValidator();
    private final EventNormalizer normalizer = new EventNormalizer(List.of(
            new DisasterSeverityNormalizer(List.of(4.0, 5.0, 6.0, 7.0)),
            new MarketSeverityNormalizer(List.of(1.0, 2.0, 5.0, 10.0)),
            new NewsSeverityNormalizer(4, 3, 1, 2)));
    private final EventIngestionService ingestion = mock(EventIngestionService.class);

    private EventSimulator simulator(long seed) {
        return new EventSimulator(ingestion, new SimulatorProperties(seed), new MutableClock(NOW));
    }

    @Test
    void theSameSeedAndClockProduceTheSameEvents() {
        assertThat(simulator(42).generate(40)).isEqualTo(simulator(42).generate(40));
    }

    @Test
    void aDifferentSeedProducesDifferentEvents() {
        assertThat(simulator(42).generate(40)).isNotEqualTo(simulator(43).generate(40));
    }

    @Test
    void successiveRunsContinueTheSequenceInsteadOfRepeatingIt() {
        EventSimulator simulator = simulator(42);

        List<NewEvent> first = simulator.generate(20);
        List<NewEvent> second = simulator.generate(20);

        Set<String> ids = new HashSet<>();
        first.forEach(event -> ids.add(event.externalId()));
        second.forEach(event -> ids.add(event.externalId()));
        assertThat(ids).hasSize(40);
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void aSplitRunProducesTheSameSequenceAsOneBigRun() {
        EventSimulator oneBig = simulator(7);
        EventSimulator split = simulator(7);

        List<NewEvent> expected = oneBig.generate(30);
        List<NewEvent> actual = new ArrayList<>(split.generate(10));
        actual.addAll(split.generate(20));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void everyGeneratedEventIsValidAndGetsASeverityFromTheRealNormalizers() {
        for (long seed : List.of(1L, 42L, 2026L, 99_999L)) {
            for (NewEvent generated : simulator(seed).generate(EventSimulator.MAX_COUNT)) {
                NewEvent validated = validator.validate(generated, NOW);
                NormalizedEvent normalized = normalizer.normalize(validated); // would throw without a derivable severity

                assertThat(normalized.severity()).isBetween(Severity.MIN_LEVEL, Severity.MAX_LEVEL);
                assertThat(generated.severity()).as("the simulator leaves severity to the normalizers").isNull();
            }
        }
    }

    @Test
    void theMixCoversAllThreeCategoriesAndSeveralSeverities() {
        List<NewEvent> events = simulator(42).generate(EventSimulator.MAX_COUNT);

        assertThat(events.stream().map(NewEvent::category).collect(Collectors.toSet()))
                .containsExactlyInAnyOrder("DISASTER", "MARKET", "NEWS");
        Set<Integer> severities = events.stream()
                .map(event -> normalizer.normalize(validator.validate(event, NOW)).severity())
                .collect(Collectors.toSet());
        assertThat(severities).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void marketEventsCarryTheAttributesTheNormalizerNeeds() {
        List<NewEvent> markets = simulator(42).generate(EventSimulator.MAX_COUNT).stream()
                .filter(event -> event.category().equals("MARKET")).toList();

        assertThat(markets).isNotEmpty();
        assertThat(markets).allSatisfy(event -> assertThat(event.attributes()).containsKeys("symbol", "changePct", "price"));
    }

    @Test
    void earthquakesCarryTheAttributesTheNormalizerNeeds() {
        List<NewEvent> quakes = simulator(42).generate(EventSimulator.MAX_COUNT).stream()
                .filter(event -> event.category().equals("DISASTER")).toList();

        assertThat(quakes).isNotEmpty();
        assertThat(quakes).allSatisfy(event -> {
            assertThat(event.attributes()).containsKeys("magnitude", "depthKm", "tsunami", "place");
            assertThat(event.title()).startsWith("M ").contains("earthquake near");
            assertThat(event.region()).isNotBlank();
        });
    }

    @Test
    void newsEventsCarryTheirFlags() {
        List<NewEvent> news = simulator(42).generate(EventSimulator.MAX_COUNT).stream()
                .filter(event -> event.category().equals("NEWS")).toList();

        assertThat(news).isNotEmpty();
        assertThat(news).allSatisfy(event -> assertThat(event.attributes())
                .containsKeys("publisher", "topics", "breaking", "developing", "minor"));
    }

    @Test
    void eventsAreDatedWithinTheLastTenMinutesAndNeverInTheFuture() {
        for (NewEvent event : simulator(42).generate(EventSimulator.MAX_COUNT)) {
            assertThat(event.occurredAt()).isAfter(NOW.minus(Duration.ofMinutes(10))).isBeforeOrEqualTo(NOW);
        }
    }

    @Test
    void everyEventUsesTheSimulatorSourceAndAUniqueRecognisableId() {
        List<NewEvent> events = simulator(42).generate(EventSimulator.MAX_COUNT);

        assertThat(events).allSatisfy(event -> {
            assertThat(event.source()).isEqualTo("simulator");
            assertThat(event.externalId()).startsWith("sim-" + NOW.toEpochMilli() + "-");
            assertThat(event.url()).isEqualTo("https://example.com/events/" + event.externalId());
        });
        assertThat(events.stream().map(NewEvent::externalId).collect(Collectors.toSet())).hasSize(EventSimulator.MAX_COUNT);
    }

    @Test
    void theCountMustBeBetweenOneAndFifty() {
        EventSimulator simulator = simulator(42);

        assertThat(simulator.generate(1)).hasSize(1);
        assertThat(simulator.generate(50)).hasSize(50);
        for (int invalid : new int[] {0, -1, 51, 1000}) {
            assertThatThrownBy(() -> simulator.generate(invalid))
                    .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.getErrors())
                            .containsExactly(new FieldError("count", "must be between 1 and 50")));
        }
    }

    @Test
    void runningSubmitsEveryGeneratedEventAndCountsNewAndDuplicates() {
        when(ingestion.ingest(any(NewEvent.class)))
                .thenReturn(new IngestResult(1, EventProcessingStatus.RECEIVED, false))
                .thenReturn(new IngestResult(2, EventProcessingStatus.RECEIVED, true))
                .thenReturn(new IngestResult(3, EventProcessingStatus.RECEIVED, false));

        SimulationResult result = simulator(42).run(3);

        assertThat(result).isEqualTo(new SimulationResult(3, 2, 1));
        ArgumentCaptor<NewEvent> submitted = ArgumentCaptor.forClass(NewEvent.class);
        verify(ingestion, times(3)).ingest(submitted.capture());
        assertThat(submitted.getAllValues()).isEqualTo(simulator(42).generate(3));
    }

    @Test
    void runningWithAnInvalidCountSubmitsNothing() {
        assertThatThrownBy(() -> simulator(42).run(0)).isInstanceOf(ValidationException.class);

        verify(ingestion, times(0)).ingest(any());
    }
}
