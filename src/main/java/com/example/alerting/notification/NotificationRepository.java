package com.example.alerting.notification;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link Notification} rows. */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
