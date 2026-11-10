package com.example.alerting.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link User} rows. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by email address.
     *
     * @param email the lower-case email address
     * @return the user, or empty if there is none
     */
    Optional<User> findByEmail(String email);
}
