package com.example.alerting.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.identity.Role;
import com.example.alerting.support.PersistenceTest;
import com.example.alerting.user.User;
import com.example.alerting.user.UserRepository;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.TestPropertySource;

/**
 * Checks the demo users that only the local profile loads (from {@code db/dev}): they exist after a start,
 * the repeatable migration runs after the versioned ones, and neither a second Flyway run nor running the
 * script by hand creates duplicates.
 */
@PersistenceTest
@TestPropertySource(properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
class DemoDataMigrationTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void thereAreThreeActiveDemoUsersWithTheirRoles() {
        assertThat(users.findAll()).extracting(User::getEmail)
                .containsExactlyInAnyOrder("admin@example.com", "alice@example.com", "bob@example.com");

        User admin = users.findByEmail("admin@example.com").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getDisplayName()).isEqualTo("Demo Admin");
        assertThat(users.findByEmail("alice@example.com").orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(users.findByEmail("bob@example.com").orElseThrow().getRole()).isEqualTo(Role.USER);
        assertThat(users.findAll()).allSatisfy(user -> assertThat(user.isActive()).isTrue());
    }

    @Test
    void theDemoMigrationIsRepeatableAndRunsAfterTheVersionedOnes() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).hasSize(3);
        MigrationInfo last = applied[applied.length - 1];
        assertThat(last.getVersion()).isNull();
        assertThat(last.getDescription()).isEqualTo("demo users");
        assertThat(last.getScript()).isEqualTo("R__demo_users.sql");
    }

    @Test
    void runningFlywayAgainChangesNothing() {
        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isZero();
        assertThat(users.count()).isEqualTo(3);
    }

    @Test
    void runningTheScriptByHandDoesNotCreateDuplicates() {
        new ResourceDatabasePopulator(new ClassPathResource("db/dev/R__demo_users.sql")).execute(dataSource);

        assertThat(users.count()).isEqualTo(3);
    }
}
