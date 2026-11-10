package com.example.alerting.event;

import com.example.alerting.shared.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Service;

/**
 * Generates realistic events for the three categories, on demand, for demos and tests (docs/02,
 * section 6.4).
 *
 * <p>The simulator is <strong>deterministic</strong>: it draws from a seeded random generator, so the same
 * seed and the same clock always produce the same events (the {@code alerting.simulator.seed} setting).
 * Successive runs continue the sequence, so they produce fresh events rather than repeats. It never sets a
 * severity: the events go through the real severity normalizers, exactly as a real feed's would, so a
 * simulated 6.4 magnitude earthquake arrives as severity 4.
 *
 * <p>Events are submitted through {@link EventIngestionService} with the source {@code simulator}, so they
 * receive the same validation and idempotency as every other event and trigger the same matching.
 */
@Service
public class EventSimulator {

    /** The source code under which simulated events are stored. */
    public static final String SOURCE = "simulator";

    /** The most events one run may generate. */
    public static final int MAX_COUNT = 50;

    private static final int EARTHQUAKE_PERCENT = 25;
    private static final int MARKET_PERCENT = 40; // the remaining 35 % are news
    private static final int MAX_AGE_SECONDS = 600;

    private record Place(String name, String region) {
    }

    private record Instrument(String symbol, String name, double price) {
    }

    private record Headline(String text, String topics, String region) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("Hualien, Taiwan", "TW"), new Place("Tohoku, Japan", "JP"),
            new Place("Valparaiso, Chile", "CL"), new Place("Anchorage, Alaska", "US-AK"),
            new Place("Izmir, Turkiye", "TR"), new Place("Christchurch, New Zealand", "NZ"),
            new Place("Los Angeles, California", "US-CA"), new Place("Jakarta, Indonesia", "ID"));

    private static final List<Instrument> INSTRUMENTS = List.of(
            new Instrument("AAPL", "Apple", 230.0), new Instrument("NVDA", "NVIDIA", 140.0),
            new Instrument("TSLA", "Tesla", 250.0), new Instrument("BTC-USD", "Bitcoin", 90000.0),
            new Instrument("GSPC", "S&P 500", 5800.0), new Instrument("EURUSD", "Euro", 1.08));

    private static final List<Headline> HEADLINES = List.of(
            new Headline("Central bank holds interest rates steady", "economy,rates", "US"),
            new Headline("Wildfire forces evacuation of coastal town", "environment,safety", "US-CA"),
            new Headline("Major airline announces new transatlantic routes", "travel,business", "GB"),
            new Headline("Parliament passes landmark climate bill", "politics,climate", "DE"),
            new Headline("Chip maker unveils next generation processor", "technology,business", "US"),
            new Headline("Heatwave breaks seasonal temperature records", "weather,climate", "ES"),
            new Headline("Ceasefire talks resume after weekend of unrest", "politics,world", null),
            new Headline("Vaccine trial reports promising early results", "health,science", null));

    private static final List<String> PUBLISHERS = List.of("Reuters", "AP", "BBC", "Bloomberg", "Al Jazeera");

    private final EventIngestionService ingestion;
    private final Clock clock;
    private final Random random;
    private long sequence;

    /**
     * Creates the simulator.
     *
     * @param ingestion where generated events are submitted
     * @param properties holds the seed
     * @param clock the time source for event times and ids
     */
    public EventSimulator(EventIngestionService ingestion, SimulatorProperties properties, Clock clock) {
        this.ingestion = ingestion;
        this.clock = clock;
        this.random = new Random(properties.seed());
    }

    /**
     * Generates events without storing them.
     *
     * @param count how many events, from 1 to {@value #MAX_COUNT}
     * @return the events: a mix of earthquakes, market moves and news, all dated within the last ten minutes
     * @throws ValidationException if the count is out of range
     */
    public synchronized List<NewEvent> generate(int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw ValidationException.of("count", "must be between 1 and " + MAX_COUNT);
        }
        Instant now = clock.instant();
        List<NewEvent> generated = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            sequence++;
            String externalId = "sim-" + now.toEpochMilli() + "-" + sequence;
            Instant occurredAt = now.minusSeconds(random.nextInt(MAX_AGE_SECONDS));
            int pick = random.nextInt(100);
            if (pick < EARTHQUAKE_PERCENT) {
                generated.add(earthquake(externalId, occurredAt));
            } else if (pick < EARTHQUAKE_PERCENT + MARKET_PERCENT) {
                generated.add(marketMove(externalId, occurredAt));
            } else {
                generated.add(newsItem(externalId, occurredAt));
            }
        }
        return List.copyOf(generated);
    }

    /**
     * Generates events and submits them through the normal ingestion path.
     *
     * <p>Generated events are valid by construction, so a failure here is an infrastructure problem and
     * propagates; events submitted before it stay stored.
     *
     * @param count how many events, from 1 to {@value #MAX_COUNT}
     * @return how many were new and how many were duplicates
     * @throws ValidationException if the count is out of range
     */
    public SimulationResult run(int count) {
        List<NewEvent> events = generate(count);
        int created = 0;
        int duplicates = 0;
        for (NewEvent event : events) {
            if (ingestion.ingest(event).duplicate()) {
                duplicates++;
            } else {
                created++;
            }
        }
        return new SimulationResult(events.size(), created, duplicates);
    }

    private NewEvent earthquake(String externalId, Instant occurredAt) {
        Place place = PLACES.get(random.nextInt(PLACES.size()));
        // Most quakes are small; the cube-like curve makes magnitude 7+ rare, as in reality.
        double magnitude = round(3.0 + 5.2 * Math.pow(random.nextDouble(), 2.5), 1);
        int depthKm = 5 + random.nextInt(60);
        boolean tsunami = magnitude >= 7.0 && random.nextBoolean();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("magnitude", magnitude);
        attributes.put("depthKm", depthKm);
        attributes.put("tsunami", tsunami);
        attributes.put("place", place.name());
        return new NewEvent(SOURCE, externalId, "DISASTER", null,
                String.format(Locale.ROOT, "M %.1f earthquake near %s", magnitude, place.name()),
                "Depth " + depthKm + " km. " + (tsunami ? "A tsunami warning has been issued." : "No tsunami warning issued."),
                url(externalId), place.region(), occurredAt, attributes);
    }

    private NewEvent marketMove(String externalId, Instant occurredAt) {
        Instrument instrument = INSTRUMENTS.get(random.nextInt(INSTRUMENTS.size()));
        // Mostly small moves with an occasional large one; the sign is independent of the size.
        double size = 0.1 + 11.0 * Math.pow(random.nextDouble(), 3);
        double changePct = round((random.nextBoolean() ? 1 : -1) * size, 2);
        double price = round(instrument.price() * (1 + changePct / 100), 2);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("symbol", instrument.symbol());
        attributes.put("changePct", changePct);
        attributes.put("price", price);
        String verb = changePct >= 0 ? "rises" : "falls";
        return new NewEvent(SOURCE, externalId, "MARKET", null,
                String.format(Locale.ROOT, "%s %s %.1f%%", instrument.name(), verb, Math.abs(changePct)),
                "Last price " + price + ", " + String.format(Locale.ROOT, "%+.2f%%", changePct) + " against the previous close.",
                url(externalId), "US", occurredAt, attributes);
    }

    private NewEvent newsItem(String externalId, Instant occurredAt) {
        Headline headline = HEADLINES.get(random.nextInt(HEADLINES.size()));
        boolean breaking = random.nextInt(10) == 0;
        boolean developing = !breaking && random.nextInt(4) == 0;
        boolean minor = !breaking && !developing && random.nextInt(3) == 0;

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("publisher", PUBLISHERS.get(random.nextInt(PUBLISHERS.size())));
        attributes.put("topics", headline.topics());
        attributes.put("breaking", breaking);
        attributes.put("developing", developing);
        attributes.put("minor", minor);
        return new NewEvent(SOURCE, externalId, "NEWS", null,
                headline.text(), null, url(externalId), headline.region(), occurredAt, attributes);
    }

    private static String url(String externalId) {
        return "https://example.com/events/" + externalId;
    }

    private static double round(double value, int places) {
        double factor = Math.pow(10, places);
        return Math.round(value * factor) / factor;
    }
}
