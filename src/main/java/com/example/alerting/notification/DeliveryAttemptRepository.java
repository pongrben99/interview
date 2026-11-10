package com.example.alerting.notification;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link DeliveryAttempt} rows. The ledger is append-only, so there are no update methods. */
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
}
