package com.example.alerting.user;

import com.example.alerting.identity.CurrentUser;
import com.example.alerting.identity.IdentityDirectory;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers the identity module's questions about users from the user table.
 *
 * <p>Only active users can be identified: a deactivated user keeps their data but can no longer call the
 * API.
 */
@Component
public class UserIdentityDirectory implements IdentityDirectory {

    private final UserRepository users;

    /**
     * Creates the directory.
     *
     * @param users where users are stored
     */
    public UserIdentityDirectory(UserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<CurrentUser> findActiveByEmail(String email) {
        return users.findByEmail(email)
                .filter(User::isActive)
                .map(user -> new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole()));
    }
}
