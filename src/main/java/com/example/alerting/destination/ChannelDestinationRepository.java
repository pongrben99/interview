package com.example.alerting.destination;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link ChannelDestination} rows. */
public interface ChannelDestinationRepository extends JpaRepository<ChannelDestination, Long> {

    /**
     * Finds a user's destination by the key that makes it unique. Saving an address that already
     * exists revives or rejects it instead of creating a duplicate.
     *
     * @param userId id of the owner
     * @param channelType the channel's type code
     * @param destinationKey the canonical key computed by the channel adapter
     * @return the destination, or empty if the user has none with that key
     */
    Optional<ChannelDestination> findByUserIdAndChannelTypeAndDestinationKey(
            long userId, String channelType, String destinationKey);
}
