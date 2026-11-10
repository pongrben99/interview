package com.example.alerting.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.event.EventCategory;
import com.example.alerting.event.EventCategoryRepository;
import com.example.alerting.event.EventSource;
import com.example.alerting.event.EventSourceRepository;
import com.example.alerting.event.SourceType;
import com.example.alerting.support.PersistenceTest;
import com.example.alerting.user.UserRepository;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks that the Flyway migrations produce the schema described in docs/02 section 5: every table,
 * the sequences, the documented indexes and the reference data. The same migrations run on every start.
 */
@PersistenceTest
class SchemaMigrationTest {

    private static final Set<String> TABLES = Set.of(
            "users", "event_categories", "event_sources", "events", "channel_destinations", "alert_rules",
            "alert_rule_destinations", "notifications", "delivery_attempts", "channel_settings", "audit_log");

    private static final Set<String> SEQUENCES = Set.of(
            "users_seq", "event_sources_seq", "events_seq", "channel_destinations_seq", "alert_rules_seq",
            "notifications_seq", "delivery_attempts_seq", "audit_log_seq");

    /** index name -> table, as listed in docs/02 section 5.4. */
    private static final Map<String, String> INDEXES = Map.ofEntries(
            Map.entry("idx_events_status_received", "events"),
            Map.entry("idx_events_category_occurred", "events"),
            Map.entry("idx_destinations_user", "channel_destinations"),
            Map.entry("idx_rules_candidates", "alert_rules"),
            Map.entry("idx_rules_user", "alert_rules"),
            Map.entry("idx_rule_destinations_destination", "alert_rule_destinations"),
            Map.entry("idx_notifications_status_next", "notifications"),
            Map.entry("idx_notifications_status_locked", "notifications"),
            Map.entry("idx_notifications_user_created", "notifications"),
            Map.entry("idx_notifications_channel_status", "notifications"),
            Map.entry("idx_notifications_rule", "notifications"),
            Map.entry("idx_notifications_event", "notifications"),
            Map.entry("idx_audit_log_created", "audit_log"));

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private EventCategoryRepository categories;

    @Autowired
    private EventSourceRepository sources;

    @Test
    void bothVersionedMigrationsWereAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).extracting(migration -> migration.getVersion().getVersion()).containsExactly("1", "2");
        assertThat(applied).extracting(MigrationInfo::getState).containsOnly(MigrationState.SUCCESS);
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void everyTableOfTheSchemaExists() throws SQLException {
        Set<String> tables = new TreeSet<>();
        try (Connection connection = dataSource.getConnection();
             ResultSet rs = connection.getMetaData().getTables(null, connection.getSchema(), "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }

        Set<String> expected = new TreeSet<>(TABLES);
        expected.add("flyway_schema_history");
        assertThat(tables).containsExactlyElementsOf(expected);
    }

    @Test
    void everySequenceIncrementsByFiftyToMatchHibernatesPooledOptimizer() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT sequence_name, increment FROM information_schema.sequences");
        Map<String, Long> increments = new HashMap<>();
        for (Map<String, Object> row : rows) {
            increments.put(((String) row.get("sequence_name")).toLowerCase(Locale.ROOT), ((Number) row.get("increment")).longValue());
        }

        assertThat(increments.keySet()).containsExactlyInAnyOrderElementsOf(SEQUENCES);
        assertThat(increments.values()).containsOnly(50L);
    }

    @Test
    void theDocumentedIndexesExist() throws SQLException {
        Map<String, Set<String>> indexesByTable = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            for (String table : Set.copyOf(INDEXES.values())) {
                Set<String> names = new TreeSet<>();
                try (ResultSet rs = connection.getMetaData().getIndexInfo(null, connection.getSchema(), table, false, false)) {
                    while (rs.next()) {
                        String name = rs.getString("INDEX_NAME");
                        if (name != null) {
                            names.add(name.toLowerCase(Locale.ROOT));
                        }
                    }
                }
                indexesByTable.put(table, names);
            }
        }

        INDEXES.forEach((index, table) -> assertThat(indexesByTable.get(table)).as("indexes of %s", table).contains(index));
    }

    @Test
    void theThreeStartingCategoriesAreLoadedAndEnabled() {
        List<EventCategory> loaded = categories.findAll();

        assertThat(loaded).extracting(EventCategory::getCode).containsExactlyInAnyOrder("NEWS", "MARKET", "DISASTER");
        assertThat(loaded).allSatisfy(category -> {
            assertThat(category.isEnabled()).isTrue();
            assertThat(category.getDisplayName()).isNotBlank();
            assertThat(category.getDescription()).isNotBlank();
        });
    }

    @Test
    void theThreeStartingSourcesAreLoadedAndOnlyTheUsgsPollerIsDisabled() {
        assertThat(sources.findAll()).extracting(EventSource::getCode)
                .containsExactlyInAnyOrder("manual", "simulator", "usgs-earthquakes");

        EventSource manual = sources.findByCode("manual").orElseThrow();
        assertThat(manual.getType()).isEqualTo(SourceType.PUSH);
        assertThat(manual.isEnabled()).isTrue();
        assertThat(manual.getConfig()).isEqualTo("{}");

        assertThat(sources.findByCode("simulator").orElseThrow().getType()).isEqualTo(SourceType.SIMULATED);
        EventSource usgs = sources.findByCode("usgs-earthquakes").orElseThrow();
        assertThat(usgs.getType()).isEqualTo(SourceType.POLL);
        assertThat(usgs.isEnabled()).isFalse();
    }

    @Test
    void theDemoUsersAreNotLoadedOutsideTheLocalDemoSetup() {
        assertThat(users.count()).isZero();
    }
}
