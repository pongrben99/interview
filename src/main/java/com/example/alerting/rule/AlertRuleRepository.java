package com.example.alerting.rule;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access to {@link AlertRule} rows. */
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {
}
