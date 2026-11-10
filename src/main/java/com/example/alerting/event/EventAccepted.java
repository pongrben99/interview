package com.example.alerting.event;

/**
 * Published once for every newly stored event, after its transaction has committed.
 *
 * <p>Matching listens for this. It is published after the commit, so a listener can always read the event
 * it refers to. A duplicate submission does not publish it. If a listener fails, the event stays
 * {@link EventProcessingStatus#RECEIVED} and the recovery sweeper picks it up again.
 *
 * @param eventId the id of the stored event
 */
public record EventAccepted(long eventId) {
}
