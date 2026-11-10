package com.example.alerting.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.admin.AuditLogEntry;
import com.example.alerting.admin.AuditLogRepository;
import com.example.alerting.channel.ChannelSetting;
import com.example.alerting.channel.ChannelSettingRepository;
import com.example.alerting.destination.ChannelDestination;
import com.example.alerting.destination.ChannelDestinationRepository;
import com.example.alerting.destination.DestinationStatus;
import com.example.alerting.event.Event;
import com.example.alerting.event.EventCategory;
import com.example.alerting.event.EventCategoryRepository;
import com.example.alerting.event.EventProcessingStatus;
import com.example.alerting.event.EventRepository;
import com.example.alerting.event.EventSource;
import com.example.alerting.event.EventSourceRepository;
import com.example.alerting.event.SourceType;
import com.example.alerting.identity.Role;
import com.example.alerting.notification.AttemptOutcome;
import com.example.alerting.notification.DeliveryAttempt;
import com.example.alerting.notification.DeliveryAttemptRepository;
import com.example.alerting.notification.Notification;
import com.example.alerting.notification.NotificationRepository;
import com.example.alerting.notification.NotificationStatus;
import com.example.alerting.notification.NotificationStatusReason;
import com.example.alerting.rule.AlertRule;
import com.example.alerting.rule.AlertRuleRepository;
import com.example.alerting.support.PersistenceTest;
import com.example.alerting.user.User;
import com.example.alerting.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Round-trip tests for every JPA entity: each is saved, flushed, evicted from the persistence context
 * and read back, and then every field is compared. This proves the entity mappings match the Flyway
 * schema column by column (Hibernate validates the mapping at start-up as well) and that the sequence
 * ids, enumerations, timestamps and optimistic-lock columns behave as designed.
 */
@PersistenceTest
class EntityMappingTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-10T09:05:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository users;

    @Autowired
    private EventCategoryRepository categories;

    @Autowired
    private EventSourceRepository sources;

    @Autowired
    private EventRepository events;

    @Autowired
    private ChannelSettingRepository channelSettings;

    @Autowired
    private ChannelDestinationRepository destinations;

    @Autowired
    private AlertRuleRepository rules;

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private DeliveryAttemptRepository attempts;

    @Autowired
    private AuditLogRepository auditLog;

    /** Flushes pending changes, empties the persistence context and reads the entity from the database. */
    private <T, I> T reload(JpaRepository<T, I> repository, I id) {
        entityManager.flush();
        entityManager.clear();
        return repository.findById(id).orElseThrow();
    }

    /** Parent rows most notifications need. */
    private record Chain(User user, EventSource source, Event event, ChannelDestination destination, AlertRule rule) {
    }

    private Chain chain(String suffix) {
        User user = users.saveAndFlush(new User("chain-" + suffix + "@example.com", "Chain " + suffix, Role.USER, T0));
        EventSource source = sources.findByCode("manual").orElseThrow();
        Event event = events.saveAndFlush(new Event(source.getId(), "chain-event-" + suffix, "NEWS", 3, "Headline",
                null, null, null, T0, T1, null));
        ChannelDestination destination = destinations.saveAndFlush(
                new ChannelDestination(user.getId(), "EMAIL", "Inbox " + suffix, "{}", "key-" + suffix, T0));
        AlertRule rule = rules.saveAndFlush(new AlertRule(user.getId(), "Rule " + suffix, null, "NEWS", 1, null, 0,
                Set.of(destination.getId()), T0));
        return new Chain(user, source, event, destination, rule);
    }

    @Test
    void aUserSurvivesARoundTrip() {
        User saved = users.saveAndFlush(new User("Alice@Example.com", "Alice", Role.ADMIN, T0));
        assertThat(saved.getId()).isNotNull();

        User loaded = reload(users, saved.getId());

        assertThat(loaded).isNotSameAs(saved);
        assertThat(loaded.getId()).isEqualTo(saved.getId());
        assertThat(loaded.getEmail()).isEqualTo("alice@example.com");
        assertThat(loaded.getDisplayName()).isEqualTo("Alice");
        assertThat(loaded.getRole()).isEqualTo(Role.ADMIN);
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(users.findByEmail("alice@example.com")).isPresent();
        assertThat(users.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void anEventCategorySurvivesARoundTrip() {
        categories.saveAndFlush(new EventCategory("HEALTH", "Health", "Outbreaks and alerts", false));

        EventCategory loaded = reload(categories, "HEALTH");

        assertThat(loaded.getCode()).isEqualTo("HEALTH");
        assertThat(loaded.getDisplayName()).isEqualTo("Health");
        assertThat(loaded.getDescription()).isEqualTo("Outbreaks and alerts");
        assertThat(loaded.isEnabled()).isFalse();
        assertThat(categories.findAll()).hasSize(4);
    }

    @Test
    void anEventSourceSurvivesARoundTripAndGetsAnIdThatDoesNotCollideWithTheSeededSources() {
        Set<Long> seededIds = sources.findAll().stream().map(EventSource::getId).collect(Collectors.toSet());

        EventSource saved = sources.saveAndFlush(new EventSource("demo-feed", "Demo feed", SourceType.POLL, false,
                "{\"url\":\"https://example.com/feed\"}", T0));
        EventSource loaded = reload(sources, saved.getId());

        assertThat(seededIds).hasSize(3).doesNotContain(saved.getId());
        assertThat(loaded.getCode()).isEqualTo("demo-feed");
        assertThat(loaded.getName()).isEqualTo("Demo feed");
        assertThat(loaded.getType()).isEqualTo(SourceType.POLL);
        assertThat(loaded.isEnabled()).isFalse();
        assertThat(loaded.getConfig()).isEqualTo("{\"url\":\"https://example.com/feed\"}");
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getLastSuccessAt()).isNull();
        assertThat(loaded.getLastErrorAt()).isNull();
        assertThat(loaded.getLastError()).isNull();
        assertThat(sources.findByCode("demo-feed")).isPresent();
        assertThat(sources.findByCode("nope")).isEmpty();
    }

    @Test
    void anEventSurvivesARoundTripAndCanBeFoundByItsIdempotencyKey() {
        EventSource source = sources.findByCode("manual").orElseThrow();
        Event saved = events.saveAndFlush(new Event(source.getId(), "ext-1", "DISASTER", 4, "M 6.4 earthquake",
                "Shallow quake", "https://example.com/e/1", "jp", T0, T1, "{\"magnitude\":6.4}"));

        Event loaded = reload(events, saved.getId());

        assertThat(loaded.getSourceId()).isEqualTo(source.getId());
        assertThat(loaded.getExternalId()).isEqualTo("ext-1");
        assertThat(loaded.getCategory()).isEqualTo("DISASTER");
        assertThat(loaded.getSeverity()).isEqualTo(4);
        assertThat(loaded.getTitle()).isEqualTo("M 6.4 earthquake");
        assertThat(loaded.getSummary()).isEqualTo("Shallow quake");
        assertThat(loaded.getUrl()).isEqualTo("https://example.com/e/1");
        assertThat(loaded.getRegion()).isEqualTo("JP");
        assertThat(loaded.getOccurredAt()).isEqualTo(T0);
        assertThat(loaded.getReceivedAt()).isEqualTo(T1);
        assertThat(loaded.getAttributes()).isEqualTo("{\"magnitude\":6.4}");
        assertThat(loaded.getProcessingStatus()).isEqualTo(EventProcessingStatus.RECEIVED);
        assertThat(loaded.getMatchedAt()).isNull();
        assertThat(loaded.getMatchCount()).isZero();
        assertThat(loaded.getMatchAttempts()).isZero();
        assertThat(loaded.getMatchError()).isNull();

        assertThat(events.findBySourceIdAndExternalId(source.getId(), "ext-1")).isPresent();
        assertThat(events.findBySourceIdAndExternalId(source.getId(), "ext-2")).isEmpty();
    }

    @Test
    void aChannelSettingSurvivesARoundTrip() {
        channelSettings.saveAndFlush(new ChannelSetting("EMAIL", T0));

        ChannelSetting loaded = reload(channelSettings, "EMAIL");

        assertThat(loaded.getChannelType()).isEqualTo("EMAIL");
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getMaxAttempts()).isNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedBy()).isNull();
    }

    @Test
    void aDestinationSurvivesARoundTripAndCanBeFoundByItsUniqueKey() {
        User user = users.saveAndFlush(new User("owner@example.com", "Owner", Role.USER, T0));
        ChannelDestination saved = destinations.saveAndFlush(new ChannelDestination(user.getId(), "SLACK", "ops-alerts",
                "{\"webhookUrl\":\"https://hooks.slack.com/services/T0/B0/XXXX\"}", "key-1", T0));

        ChannelDestination loaded = reload(destinations, saved.getId());

        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getChannelType()).isEqualTo("SLACK");
        assertThat(loaded.getLabel()).isEqualTo("ops-alerts");
        assertThat(loaded.getConfig()).isEqualTo("{\"webhookUrl\":\"https://hooks.slack.com/services/T0/B0/XXXX\"}");
        assertThat(loaded.getDestinationKey()).isEqualTo("key-1");
        assertThat(loaded.getStatus()).isEqualTo(DestinationStatus.ACTIVE);
        assertThat(loaded.getDeletedAt()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getVersion()).isZero();

        assertThat(destinations.findByUserIdAndChannelTypeAndDestinationKey(user.getId(), "SLACK", "key-1")).isPresent();
        assertThat(destinations.findByUserIdAndChannelTypeAndDestinationKey(user.getId(), "SLACK", "other")).isEmpty();
        assertThat(destinations.findByUserIdAndChannelTypeAndDestinationKey(user.getId(), "EMAIL", "key-1")).isEmpty();
    }

    @Test
    void anAlertRuleSurvivesARoundTripTogetherWithItsDestinationIds() {
        User user = users.saveAndFlush(new User("rules@example.com", "Rules", Role.USER, T0));
        ChannelDestination slack = destinations.saveAndFlush(new ChannelDestination(user.getId(), "SLACK", "ops", "{}", "k1", T0));
        ChannelDestination email = destinations.saveAndFlush(new ChannelDestination(user.getId(), "EMAIL", "me", "{}", "k2", T0));
        AlertRule saved = rules.saveAndFlush(new AlertRule(user.getId(), "Big quakes", "Strong quakes", "DISASTER", 4,
                "{\"regions\":[\"JP\"]}", 300, Set.of(slack.getId(), email.getId()), T0));

        AlertRule loaded = reload(rules, saved.getId());

        assertThat(loaded.getUserId()).isEqualTo(user.getId());
        assertThat(loaded.getName()).isEqualTo("Big quakes");
        assertThat(loaded.getDescription()).isEqualTo("Strong quakes");
        assertThat(loaded.getCategory()).isEqualTo("DISASTER");
        assertThat(loaded.getMinSeverity()).isEqualTo(4);
        assertThat(loaded.getCriteria()).isEqualTo("{\"regions\":[\"JP\"]}");
        assertThat(loaded.getCooldownSeconds()).isEqualTo(300);
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getLastTriggeredAt()).isNull();
        assertThat(loaded.getDeletedAt()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getVersion()).isZero();
        assertThat(loaded.getDestinationIds()).containsExactlyInAnyOrder(slack.getId(), email.getId());
    }

    @Test
    void aPendingNotificationSurvivesARoundTrip() {
        Chain chain = chain("pending");
        Notification saved = notifications.saveAndFlush(Notification.pending(chain.event().getId(), chain.rule().getId(),
                chain.destination().getId(), chain.user().getId(), "EMAIL", 5, T0));

        Notification loaded = reload(notifications, saved.getId());

        assertThat(loaded.getEventId()).isEqualTo(chain.event().getId());
        assertThat(loaded.getAlertRuleId()).isEqualTo(chain.rule().getId());
        assertThat(loaded.getDestinationId()).isEqualTo(chain.destination().getId());
        assertThat(loaded.getUserId()).isEqualTo(chain.user().getId());
        assertThat(loaded.getChannelType()).isEqualTo("EMAIL");
        assertThat(loaded.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(loaded.getStatusReason()).isNull();
        assertThat(loaded.getAttemptCount()).isZero();
        assertThat(loaded.getMaxAttempts()).isEqualTo(5);
        assertThat(loaded.getNextAttemptAt()).isEqualTo(T0);
        assertThat(loaded.getLockedBy()).isNull();
        assertThat(loaded.getLockedUntil()).isNull();
        assertThat(loaded.getLastErrorCode()).isNull();
        assertThat(loaded.getLastErrorMessage()).isNull();
        assertThat(loaded.getCreatedAt()).isEqualTo(T0);
        assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
        assertThat(loaded.getSentAt()).isNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void aSuppressedNotificationKeepsItsReason() {
        Chain chain = chain("suppressed");
        Notification saved = notifications.saveAndFlush(Notification.suppressedByCooldown(chain.event().getId(),
                chain.rule().getId(), chain.destination().getId(), chain.user().getId(), "EMAIL", 5, T0));

        Notification loaded = reload(notifications, saved.getId());

        assertThat(loaded.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
        assertThat(loaded.getStatusReason()).isEqualTo(NotificationStatusReason.COOLDOWN);
    }

    @Test
    void aDeliveryAttemptSurvivesARoundTrip() {
        Chain chain = chain("attempt");
        Notification notification = notifications.saveAndFlush(Notification.pending(chain.event().getId(),
                chain.rule().getId(), chain.destination().getId(), chain.user().getId(), "EMAIL", 5, T0));
        DeliveryAttempt failed = attempts.saveAndFlush(new DeliveryAttempt(notification.getId(), 1, T0, T1,
                AttemptOutcome.RETRYABLE_FAILURE, "HTTP_503", "Service unavailable", 503, 1234, "msg-1", "worker-a"));
        DeliveryAttempt succeeded = attempts.saveAndFlush(new DeliveryAttempt(notification.getId(), 2, T1, T1,
                AttemptOutcome.SUCCESS, null, null, null, 0, null, "worker-b"));

        DeliveryAttempt loadedFailed = reload(attempts, failed.getId());
        assertThat(loadedFailed.getNotificationId()).isEqualTo(notification.getId());
        assertThat(loadedFailed.getAttemptNo()).isEqualTo(1);
        assertThat(loadedFailed.getStartedAt()).isEqualTo(T0);
        assertThat(loadedFailed.getFinishedAt()).isEqualTo(T1);
        assertThat(loadedFailed.getOutcome()).isEqualTo(AttemptOutcome.RETRYABLE_FAILURE);
        assertThat(loadedFailed.getErrorCode()).isEqualTo("HTTP_503");
        assertThat(loadedFailed.getErrorMessage()).isEqualTo("Service unavailable");
        assertThat(loadedFailed.getHttpStatus()).isEqualTo(503);
        assertThat(loadedFailed.getLatencyMs()).isEqualTo(1234);
        assertThat(loadedFailed.getProviderMessageId()).isEqualTo("msg-1");
        assertThat(loadedFailed.getWorkerId()).isEqualTo("worker-a");

        DeliveryAttempt loadedSucceeded = reload(attempts, succeeded.getId());
        assertThat(loadedSucceeded.getOutcome()).isEqualTo(AttemptOutcome.SUCCESS);
        assertThat(loadedSucceeded.getErrorCode()).isNull();
        assertThat(loadedSucceeded.getErrorMessage()).isNull();
        assertThat(loadedSucceeded.getHttpStatus()).isNull();
        assertThat(loadedSucceeded.getProviderMessageId()).isNull();
        assertThat(attempts.findAll()).extracting(DeliveryAttempt::getAttemptNo).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void anAuditLogEntrySurvivesARoundTrip() {
        User admin = users.saveAndFlush(new User("audit-admin@example.com", "Audit Admin", Role.ADMIN, T0));
        AuditLogEntry saved = auditLog.saveAndFlush(new AuditLogEntry(admin.getId(), "REQUEUE_NOTIFICATION",
                "NOTIFICATION", "42", "{\"reason\":\"webhook fixed\"}", T1));

        AuditLogEntry loaded = reload(auditLog, saved.getId());

        assertThat(loaded.getActorUserId()).isEqualTo(admin.getId());
        assertThat(loaded.getAction()).isEqualTo("REQUEUE_NOTIFICATION");
        assertThat(loaded.getTargetType()).isEqualTo("NOTIFICATION");
        assertThat(loaded.getTargetId()).isEqualTo("42");
        assertThat(loaded.getDetails()).isEqualTo("{\"reason\":\"webhook fixed\"}");
        assertThat(loaded.getCreatedAt()).isEqualTo(T1);
    }

    @Test
    void idsComeFromTheSequenceAndAreUnique() {
        List<User> saved = users.saveAllAndFlush(List.of(
                new User("a@example.com", "A", Role.USER, T0),
                new User("b@example.com", "B", Role.USER, T0),
                new User("c@example.com", "C", Role.USER, T0)));

        assertThat(saved).extracting(User::getId).doesNotContainNull().doesNotHaveDuplicates();
    }
}
