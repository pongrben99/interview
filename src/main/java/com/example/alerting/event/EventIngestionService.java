package com.example.alerting.event;

import com.example.alerting.shared.DomainException;
import com.example.alerting.shared.ErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The one way an event enters the system (docs/02, section 6.5).
 *
 * <p>The push API, the simulator and any future connector all call {@link #ingest}, so validation,
 * idempotency and matching are identical whatever the origin of an event:
 *
 * <ol>
 *   <li><strong>Validate</strong> and clean the payload ({@link EventValidator}).</li>
 *   <li><strong>Resolve</strong> the source and the category; both must exist and be enabled.</li>
 *   <li><strong>Normalize</strong>: severity, derived attributes, external id ({@link EventNormalizer}).</li>
 *   <li><strong>Deduplicate and store.</strong> An event is unique per source and external id. A repeat
 *       submission is answered with the stored event and changes nothing.</li>
 *   <li><strong>Announce</strong> {@link EventAccepted}, after the commit.</li>
 * </ol>
 *
 * <h2>Why the insert is its own short transaction</h2>
 * Two senders can submit the same event at the same instant; both may find nothing and both try to insert.
 * The database's unique key lets exactly one win. The loser's transaction fails, so the failure is caught
 * <em>outside</em> that transaction, the stored event is read again, and the answer is "duplicate". Running
 * the insert in {@code REQUIRES_NEW} keeps this true even if a caller already holds a transaction, and keeps
 * the lock window to a single statement. This method is therefore deliberately not transactional itself.
 *
 * <p>Nothing here swallows infrastructure failures: if the database is unavailable the exception propagates,
 * nothing has been accepted, and the sender may safely retry.
 */
@Service
public class EventIngestionService {

    /** The source used when a submission does not name one. */
    public static final String DEFAULT_SOURCE = "manual";

    private static final Logger LOG = LoggerFactory.getLogger(EventIngestionService.class);

    private final EventValidator validator;
    private final EventNormalizer normalizer;
    private final AttributesCodec attributesCodec;
    private final EventSourceRepository sources;
    private final EventCategoryRepository categories;
    private final EventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final TransactionTemplate insertTransaction;

    /**
     * Creates the service.
     *
     * @param validator checks and cleans payloads
     * @param normalizer derives severity, attributes and external ids
     * @param attributesCodec writes the attributes as JSON
     * @param sources the event sources
     * @param categories the event categories
     * @param events the event store
     * @param publisher announces accepted events
     * @param clock the time source
     * @param transactionManager runs the insert in its own transaction
     */
    public EventIngestionService(EventValidator validator, EventNormalizer normalizer, AttributesCodec attributesCodec,
                                 EventSourceRepository sources, EventCategoryRepository categories,
                                 EventRepository events, ApplicationEventPublisher publisher, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.validator = validator;
        this.normalizer = normalizer;
        this.attributesCodec = attributesCodec;
        this.sources = sources;
        this.categories = categories;
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
        this.insertTransaction = new TransactionTemplate(transactionManager);
        this.insertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Accepts an event.
     *
     * @param submitted the event as the sender provided it
     * @return the stored event's id and status; {@link IngestResult#duplicate()} tells whether it was new
     * @throws com.example.alerting.shared.ValidationException if the payload breaks a field rule, or the
     *         severity is missing and cannot be derived
     * @throws DomainException with code {@code SOURCE_INVALID} or {@code CATEGORY_INVALID} (kind INVALID) if the
     *         source or category does not exist, with code {@code SOURCE_DISABLED} (kind CONFLICT) if the
     *         source is disabled
     */
    public IngestResult ingest(NewEvent submitted) {
        Instant now = clock.instant();
        NewEvent event = validator.validate(submitted, now);
        EventSource source = resolveSource(event.source() == null ? DEFAULT_SOURCE : event.source());
        requireUsableCategory(event.category());
        NormalizedEvent normalized = normalizer.normalize(event);
        String attributesJson = attributesCodec.toJson(normalized.attributes());
        long sourceId = Objects.requireNonNull(source.getId(), "a stored source has an id");

        Optional<Event> existing = events.findBySourceIdAndExternalId(sourceId, normalized.externalId());
        if (existing.isPresent()) {
            return duplicateOf(existing.get());
        }

        Event candidate = new Event(sourceId, normalized.externalId(), event.category(), normalized.severity(),
                event.title(), event.summary(), event.url(), event.region(), event.occurredAt(), now, attributesJson);
        Event stored;
        try {
            stored = insertTransaction.execute(status -> events.saveAndFlush(candidate));
        } catch (DataIntegrityViolationException violation) {
            // Lost a race with an identical submission, or hit a different constraint. Read again to tell.
            Event winner = events.findBySourceIdAndExternalId(sourceId, normalized.externalId())
                    .orElseThrow(() -> violation);
            return duplicateOf(winner);
        }
        long eventId = Objects.requireNonNull(Objects.requireNonNull(stored, "insert returned no event").getId());
        LOG.info("Accepted event {} (category {}, severity {})", eventId, stored.getCategory(), stored.getSeverity());
        announce(eventId);
        return new IngestResult(eventId, stored.getProcessingStatus(), false);
    }

    private EventSource resolveSource(String code) {
        EventSource source = sources.findByCode(code).orElseThrow(() -> new DomainException(
                DomainException.Kind.INVALID, ErrorCodes.SOURCE_INVALID, "Unknown source '" + code + "'."));
        if (!source.isEnabled()) {
            throw new DomainException(DomainException.Kind.CONFLICT, ErrorCodes.SOURCE_DISABLED,
                    "Source '" + code + "' is disabled and does not accept events.");
        }
        return source;
    }

    private void requireUsableCategory(String code) {
        boolean usable = categories.findById(code).filter(EventCategory::isEnabled).isPresent();
        if (!usable) {
            throw new DomainException(DomainException.Kind.INVALID, ErrorCodes.CATEGORY_INVALID,
                    "Unknown or disabled category '" + code + "'.");
        }
    }

    private static IngestResult duplicateOf(Event stored) {
        return new IngestResult(Objects.requireNonNull(stored.getId()), stored.getProcessingStatus(), true);
    }

    /**
     * Announces a stored event. The event is already committed, so a failing listener must not turn a
     * successful ingestion into an error: the event stays RECEIVED and the recovery sweeper retries it.
     */
    private void announce(long eventId) {
        try {
            publisher.publishEvent(new EventAccepted(eventId));
        } catch (RuntimeException e) {
            LOG.error("Event {} is stored but announcing it failed; the recovery sweeper will pick it up", eventId, e);
        }
    }
}
