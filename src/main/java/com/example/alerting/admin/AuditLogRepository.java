package com.example.alerting.admin;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link AuditLogEntry} rows. The log is append-only, so there are no update methods. */
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {
}
