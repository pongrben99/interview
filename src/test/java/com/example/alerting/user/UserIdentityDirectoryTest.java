package com.example.alerting.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.identity.CurrentUser;
import com.example.alerting.identity.Role;
import com.example.alerting.support.PersistenceTest;
import com.example.alerting.support.TestEvents;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** The identity module's view of the user table: only active users can be identified. */
@PersistenceTest
class UserIdentityDirectoryTest {

    @Autowired
    private UserIdentityDirectory directory;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anActiveUserIsFoundWithTheirIdNameAndRole() {
        User carol = users.saveAndFlush(new User("carol@example.com", "Carol", Role.ADMIN, TestEvents.NOW));

        assertThat(directory.findActiveByEmail("carol@example.com"))
                .contains(new CurrentUser(carol.getId(), "carol@example.com", "Carol", Role.ADMIN));
    }

    @Test
    void aDeactivatedUserIsNotFound() {
        users.saveAndFlush(new User("dave@example.com", "Dave", Role.USER, TestEvents.NOW));
        jdbc.update("UPDATE users SET active = FALSE WHERE email = ?", "dave@example.com");
        entityManager.clear(); // forget the cached entity so the lookup reads the deactivated row

        assertThat(directory.findActiveByEmail("dave@example.com")).isEmpty();
    }

    @Test
    void anUnknownEmailIsNotFound() {
        assertThat(directory.findActiveByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void theLookupExpectsTheLowerCaseAddressTheResolverPassesIn() {
        users.saveAndFlush(new User("Erin@Example.com", "Erin", Role.USER, TestEvents.NOW)); // stored lower-case

        assertThat(directory.findActiveByEmail("erin@example.com")).isPresent();
        assertThat(directory.findActiveByEmail("Erin@Example.com")).isEmpty();
    }
}
