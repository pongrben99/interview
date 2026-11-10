package com.example.alerting.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.example.alerting.support.PersistenceTest;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves that the database itself enforces the rules of docs/02 section 5, independently of the Java
 * code: unique keys (which make ingestion and matching idempotent), foreign keys, CHECK constraints on
 * every enumeration and range, and that valid boundary values are still accepted.
 *
 * <p>The tests insert rows with plain SQL. Every rejection is checked against the constraint's
 * <em>name</em>, so a test cannot pass because some other constraint happened to fire.
 */
@PersistenceTest
class ConstraintTest {

    // SQL expressions that point at the rows created in createParentRows().
    private static final String USER = "(SELECT id FROM users WHERE email = 'fixture@example.com')";
    private static final String SOURCE = "(SELECT id FROM event_sources WHERE code = 'manual')";
    private static final String EVENT = "(SELECT id FROM events WHERE external_id = 'fixture-event')";
    private static final String EVENT_2 = "(SELECT id FROM events WHERE external_id = 'fixture-event-2')";
    private static final String DESTINATION = "(SELECT id FROM channel_destinations WHERE destination_key = 'fixture-key')";
    private static final String RULE = "(SELECT id FROM alert_rules WHERE name = 'fixture-rule')";
    private static final String NOTIFICATION = "(SELECT id FROM notifications WHERE channel_type = 'FIXTURE')";
    private static final String MISSING = "999999";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void createParentRows() {
        jdbc.update(user("fixture@example.com", "USER"));
        jdbc.update(event(SOURCE, "fixture-event", "NEWS", 3, "RECEIVED"));
        jdbc.update(event(SOURCE, "fixture-event-2", "NEWS", 3, "RECEIVED"));
        jdbc.update(destination(USER, "EMAIL", "fixture-key", "ACTIVE"));
        // The rule uses MARKET while the events use NEWS, so each category is referenced from one table only.
        jdbc.update(rule(USER, "fixture-rule", "MARKET", 1, 0));
        jdbc.update(ruleDestination(RULE, DESTINATION));
        jdbc.update(notification(EVENT, RULE, DESTINATION, USER, "PENDING"));
        jdbc.update(attempt(NOTIFICATION, 1, "RETRYABLE_FAILURE"));
    }

    // ---------------------------------------------------------------------------------------------
    // Unique keys
    // ---------------------------------------------------------------------------------------------

    @Test
    void anEmailAddressBelongsToOneUser() {
        assertViolation(() -> jdbc.update(user("fixture@example.com", "ADMIN")), "uq_users_email");
    }

    @Test
    void aSourceCodeBelongsToOneSource() {
        assertViolation(() -> jdbc.update(source("manual", "PUSH")), "uq_event_sources_code");
    }

    @Test
    void anEventIsStoredOncePerSourceAndExternalId() {
        assertViolation(() -> jdbc.update(event(SOURCE, "fixture-event", "MARKET", 5, "RECEIVED")),
                "uq_events_source_external");
    }

    @Test
    void theSameExternalIdIsAllowedFromAnotherSource() {
        jdbc.update(source("other-source", "PUSH"));

        jdbc.update(event("(SELECT id FROM event_sources WHERE code = 'other-source')", "fixture-event", "NEWS", 3, "RECEIVED"));

        assertThat(count("events", "external_id = 'fixture-event'")).isEqualTo(2);
    }

    @Test
    void aUserSavesAnAddressOncePerChannel() {
        assertViolation(() -> jdbc.update(destination(USER, "EMAIL", "fixture-key", "DISABLED")),
                "uq_destinations_user_channel_key");
    }

    @Test
    void theSameAddressIsAllowedOnAnotherChannelOrForAnotherUser() {
        jdbc.update(destination(USER, "SLACK", "fixture-key", "ACTIVE"));
        jdbc.update(user("second@example.com", "USER"));
        jdbc.update(destination("(SELECT id FROM users WHERE email = 'second@example.com')", "EMAIL", "fixture-key", "ACTIVE"));

        assertThat(count("channel_destinations", "destination_key = 'fixture-key'")).isEqualTo(3);
    }

    @Test
    void aNotificationExistsOncePerEventRuleAndDestination() {
        assertViolation(() -> jdbc.update(notification(EVENT, RULE, DESTINATION, USER, "SENT")),
                "uq_notifications_event_rule_destination");
    }

    @Test
    void theSameEventAndRuleMayNotifyAnotherDestination() {
        jdbc.update(destination(USER, "SLACK", "second-key", "ACTIVE"));

        jdbc.update(notification(EVENT, RULE, "(SELECT id FROM channel_destinations WHERE destination_key = 'second-key')",
                USER, "PENDING"));

        assertThat(count("notifications", "event_id = " + EVENT)).isEqualTo(2);
    }

    @Test
    void anAttemptNumberIsUsedOncePerNotification() {
        assertViolation(() -> jdbc.update(attempt(NOTIFICATION, 1, "SUCCESS")), "uq_attempts_notification_no");
    }

    @Test
    void theSameAttemptNumberIsAllowedForAnotherNotification() {
        jdbc.update(event(SOURCE, "second-event", "NEWS", 3, "RECEIVED"));
        jdbc.update(notification("(SELECT id FROM events WHERE external_id = 'second-event')", RULE, DESTINATION, USER, "PENDING")
                .replace("'FIXTURE'", "'SECOND'"));

        jdbc.update(attempt("(SELECT id FROM notifications WHERE channel_type = 'SECOND')", 1, "SUCCESS"));

        assertThat(count("delivery_attempts", "attempt_no = 1")).isEqualTo(2);
    }

    // ---------------------------------------------------------------------------------------------
    // Foreign keys
    // ---------------------------------------------------------------------------------------------

    static Stream<Arguments> foreignKeyViolations() {
        return Stream.of(
                arguments("event -> unknown source", "fk_events_source", event(MISSING, "x", "NEWS", 3, "RECEIVED")),
                arguments("event -> unknown category", "fk_events_category", event(SOURCE, "x", "NOPE", 3, "RECEIVED")),
                arguments("destination -> unknown user", "fk_destinations_user", destination(MISSING, "EMAIL", "x", "ACTIVE")),
                arguments("rule -> unknown user", "fk_rules_user", rule(MISSING, "x", "NEWS", 1, 0)),
                arguments("rule -> unknown category", "fk_rules_category", rule(USER, "x", "NOPE", 1, 0)),
                arguments("rule destination -> unknown rule", "fk_rule_destinations_rule", ruleDestination(MISSING, DESTINATION)),
                arguments("rule destination -> unknown destination", "fk_rule_destinations_destination",
                        ruleDestination(RULE, MISSING)),
                arguments("notification -> unknown event", "fk_notifications_event",
                        notification(MISSING, RULE, DESTINATION, USER, "PENDING")),
                arguments("notification -> unknown rule", "fk_notifications_rule",
                        notification(EVENT, MISSING, DESTINATION, USER, "PENDING")),
                arguments("notification -> unknown destination", "fk_notifications_destination",
                        notification(EVENT, RULE, MISSING, USER, "PENDING")),
                // EVENT_2 keeps the (event, rule, destination) triple unique, so only the user reference can fail.
                arguments("notification -> unknown user", "fk_notifications_user",
                        notification(EVENT_2, RULE, DESTINATION, MISSING, "PENDING")),
                arguments("attempt -> unknown notification", "fk_attempts_notification", attempt(MISSING, 9, "SUCCESS")),
                arguments("channel setting -> unknown admin", "fk_channel_settings_user", channelSetting("SLACK", MISSING)),
                arguments("audit entry -> unknown actor", "fk_audit_log_actor", auditLog(MISSING)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("foreignKeyViolations")
    void everyReferenceMustPointAtAnExistingRow(String description, String constraint, String sql) {
        assertViolation(() -> jdbc.update(sql), constraint);
    }

    static Stream<Arguments> referencedRowsThatCannotBeDeleted() {
        return Stream.of(
                arguments("source still used by an event", "fk_events_source",
                        "DELETE FROM event_sources WHERE code = 'manual'"),
                arguments("category still used by an event", "fk_events_category",
                        "DELETE FROM event_categories WHERE code = 'NEWS'"),
                arguments("category still used by a rule", "fk_rules_category",
                        "DELETE FROM event_categories WHERE code = 'MARKET'"),
                arguments("event still used by a notification", "fk_notifications_event",
                        "DELETE FROM events WHERE external_id = 'fixture-event'"),
                arguments("notification still used by an attempt", "fk_attempts_notification",
                        "DELETE FROM notifications WHERE channel_type = 'FIXTURE'"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("referencedRowsThatCannotBeDeleted")
    void aRowThatIsStillReferencedCannotBeDeleted(String description, String constraint, String sql) {
        assertViolation(() -> jdbc.update(sql), constraint);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "DELETE FROM users WHERE email = 'fixture@example.com'",
            "DELETE FROM alert_rules WHERE name = 'fixture-rule'",
            "DELETE FROM channel_destinations WHERE destination_key = 'fixture-key'"})
    void rowsReferencedFromSeveralTablesCannotBeDeletedEither(String sql) {
        assertThatThrownBy(() -> jdbc.update(sql))
                .isInstanceOf(DataIntegrityViolationException.class)
                .message().containsIgnoringCase("referential integrity");
    }

    // ---------------------------------------------------------------------------------------------
    // CHECK constraints: values outside the allowed set or range are rejected
    // ---------------------------------------------------------------------------------------------

    static Stream<Arguments> checkViolations() {
        return Stream.of(
                arguments("user role", "ck_users_role", user("role@example.com", "ROOT")),
                arguments("source type", "ck_event_sources_type", source("s", "PULL")),
                arguments("event severity 0", "ck_events_severity", event(SOURCE, "e", "NEWS", 0, "RECEIVED")),
                arguments("event severity 6", "ck_events_severity", event(SOURCE, "e", "NEWS", 6, "RECEIVED")),
                arguments("event processing status", "ck_events_processing_status", event(SOURCE, "e", "NEWS", 3, "DONE")),
                arguments("destination status", "ck_destinations_status", destination(USER, "EMAIL", "k", "PAUSED")),
                arguments("rule minimum severity 0", "ck_rules_min_severity", rule(USER, "r", "NEWS", 0, 0)),
                arguments("rule minimum severity 6", "ck_rules_min_severity", rule(USER, "r", "NEWS", 6, 0)),
                arguments("rule cooldown -1", "ck_rules_cooldown", rule(USER, "r", "NEWS", 1, -1)),
                arguments("rule cooldown 86401", "ck_rules_cooldown", rule(USER, "r", "NEWS", 1, 86401)),
                arguments("notification status", "ck_notifications_status",
                        notification(EVENT_2, RULE, DESTINATION, USER, "LOST")),
                arguments("attempt outcome", "ck_attempts_outcome", attempt(NOTIFICATION, 2, "MAYBE")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("checkViolations")
    void valuesOutsideTheAllowedSetOrRangeAreRejected(String description, String constraint, String sql) {
        assertViolation(() -> jdbc.update(sql), constraint);
    }

    @ParameterizedTest(name = "event severity {0} is accepted")
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void everySeverityOfTheScaleIsAccepted(int severity) {
        jdbc.update(event(SOURCE, "sev-" + severity, "NEWS", severity, "RECEIVED"));

        assertThat(count("events", "external_id = 'sev-" + severity + "'")).isEqualTo(1);
    }

    @ParameterizedTest(name = "rule with minimum severity {0} and cooldown {1} is accepted")
    @MethodSource("ruleBoundaries")
    void theBoundariesOfARuleAreAccepted(int minSeverity, int cooldown) {
        jdbc.update(rule(USER, "boundary-" + minSeverity + "-" + cooldown, "NEWS", minSeverity, cooldown));

        assertThat(count("alert_rules", "name = 'boundary-" + minSeverity + "-" + cooldown + "'")).isEqualTo(1);
    }

    static Stream<Arguments> ruleBoundaries() {
        return Stream.of(arguments(1, 0), arguments(5, 0), arguments(1, 86400), arguments(5, 86400));
    }

    @ParameterizedTest(name = "event status {0} is accepted")
    @ValueSource(strings = {"RECEIVED", "MATCHED", "SKIPPED_STALE", "FAILED"})
    void everyEventProcessingStatusIsAccepted(String status) {
        jdbc.update(event(SOURCE, "status-" + status, "NEWS", 3, status));

        assertThat(count("events", "processing_status = '" + status + "'")).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "notification status {0} is accepted")
    @ValueSource(strings = {"PENDING", "IN_FLIGHT", "RETRY_SCHEDULED", "SENT", "DEAD", "CANCELLED", "SUPPRESSED"})
    void everyNotificationStatusIsAccepted(String status) {
        jdbc.update(event(SOURCE, "event-for-" + status, "NEWS", 3, "RECEIVED"));

        jdbc.update(notification("(SELECT id FROM events WHERE external_id = 'event-for-" + status + "')", RULE,
                DESTINATION, USER, status).replace("'FIXTURE'", "'" + status + "'"));

        assertThat(count("notifications", "status = '" + status + "'")).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "attempt outcome {0} is accepted")
    @ValueSource(strings = {"SUCCESS", "RETRYABLE_FAILURE", "PERMANENT_FAILURE", "LEASE_EXPIRED"})
    void everyAttemptOutcomeIsAccepted(String outcome) {
        jdbc.update(attempt(NOTIFICATION, 10 + outcome.length(), outcome));

        assertThat(count("delivery_attempts", "outcome = '" + outcome + "'")).isGreaterThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "role {0}, source type {1}, destination status {2} are accepted")
    @MethodSource("enumerationValues")
    void theRemainingEnumerationValuesAreAccepted(String role, String sourceType, String destinationStatus) {
        jdbc.update(user("role-" + role + "@example.com", role));
        jdbc.update(source("source-" + sourceType, sourceType));
        jdbc.update(destination(USER, "EMAIL", "status-" + destinationStatus, destinationStatus));

        assertThat(count("users", "role = '" + role + "'")).isGreaterThanOrEqualTo(1);
        assertThat(count("event_sources", "type = '" + sourceType + "'")).isGreaterThanOrEqualTo(1);
        assertThat(count("channel_destinations", "status = '" + destinationStatus + "'")).isGreaterThanOrEqualTo(1);
    }

    static Stream<Arguments> enumerationValues() {
        return Stream.of(arguments("USER", "PUSH", "ACTIVE"), arguments("ADMIN", "POLL", "DISABLED"),
                arguments("USER", "SIMULATED", "ACTIVE"));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void assertViolation(ThrowingCallable statement, String constraint) {
        assertThatThrownBy(statement)
                .isInstanceOf(DataIntegrityViolationException.class)
                .message().containsIgnoringCase(constraint);
    }

    private int count(String table, String condition) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class);
        return count == null ? 0 : count;
    }

    private static String user(String email, String role) {
        return "INSERT INTO users (id, email, display_name, role, active, created_at, updated_at) VALUES "
                + "(NEXTVAL('users_seq'), '" + email + "', 'Test', '" + role + "', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String source(String code, String type) {
        return "INSERT INTO event_sources (id, code, name, type, enabled, config, created_at) VALUES "
                + "(NEXTVAL('event_sources_seq'), '" + code + "', 'Test source', '" + type + "', TRUE, '{}', CURRENT_TIMESTAMP)";
    }

    private static String event(String sourceId, String externalId, String category, int severity, String status) {
        return "INSERT INTO events (id, source_id, external_id, category, severity, title, occurred_at, received_at, "
                + "attributes, processing_status) VALUES (NEXTVAL('events_seq'), " + sourceId + ", '" + externalId + "', '"
                + category + "', " + severity + ", 'Title', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '{}', '" + status + "')";
    }

    private static String destination(String userId, String channel, String key, String status) {
        return "INSERT INTO channel_destinations (id, user_id, channel_type, label, config, destination_key, status, "
                + "created_at, updated_at) VALUES (NEXTVAL('channel_destinations_seq'), " + userId + ", '" + channel
                + "', 'Label', '{}', '" + key + "', '" + status + "', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String rule(String userId, String name, String category, int minSeverity, int cooldown) {
        return "INSERT INTO alert_rules (id, user_id, name, category, min_severity, cooldown_seconds, created_at, "
                + "updated_at) VALUES (NEXTVAL('alert_rules_seq'), " + userId + ", '" + name + "', '" + category + "', "
                + minSeverity + ", " + cooldown + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String ruleDestination(String ruleId, String destinationId) {
        return "INSERT INTO alert_rule_destinations (rule_id, destination_id) VALUES (" + ruleId + ", " + destinationId + ")";
    }

    private static String notification(String eventId, String ruleId, String destinationId, String userId, String status) {
        return "INSERT INTO notifications (id, event_id, alert_rule_id, destination_id, user_id, channel_type, status, "
                + "max_attempts, next_attempt_at, created_at, updated_at) VALUES (NEXTVAL('notifications_seq'), " + eventId
                + ", " + ruleId + ", " + destinationId + ", " + userId + ", 'FIXTURE', '" + status
                + "', 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
    }

    private static String attempt(String notificationId, int attemptNo, String outcome) {
        return "INSERT INTO delivery_attempts (id, notification_id, attempt_no, started_at, finished_at, outcome, "
                + "latency_ms, worker_id) VALUES (NEXTVAL('delivery_attempts_seq'), " + notificationId + ", " + attemptNo
                + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '" + outcome + "', 12, 'worker')";
    }

    private static String channelSetting(String channelType, String updatedBy) {
        return "INSERT INTO channel_settings (channel_type, enabled, updated_at, updated_by) VALUES ('" + channelType
                + "', TRUE, CURRENT_TIMESTAMP, " + updatedBy + ")";
    }

    private static String auditLog(String actorId) {
        return "INSERT INTO audit_log (id, actor_user_id, action, target_type, target_id, created_at) VALUES "
                + "(NEXTVAL('audit_log_seq'), " + actorId + ", 'ACTION', 'TYPE', '1', CURRENT_TIMESTAMP)";
    }
}
