package com.example.alerting.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the creation rules of {@link DeliveryAttempt}, the append-only delivery ledger entry. */
class DeliveryAttemptTest {

    private static final Instant START = Instant.parse("2026-11-10T12:00:00Z");
    private static final Instant END = Instant.parse("2026-11-10T12:00:01Z");

    private static DeliveryAttempt attempt(int attemptNo, Instant started, Instant finished, int latencyMs) {
        return new DeliveryAttempt(21L, attemptNo, started, finished, AttemptOutcome.RETRYABLE_FAILURE,
                "HTTP_503", "Service unavailable", 503, latencyMs, "msg-1", "worker-a");
    }

    @Test
    void anAttemptKeepsAllGivenFields() {
        DeliveryAttempt attempt = attempt(2, START, END, 1000);

        assertThat(attempt.getId()).isNull();
        assertThat(attempt.getNotificationId()).isEqualTo(21L);
        assertThat(attempt.getAttemptNo()).isEqualTo(2);
        assertThat(attempt.getStartedAt()).isEqualTo(START);
        assertThat(attempt.getFinishedAt()).isEqualTo(END);
        assertThat(attempt.getOutcome()).isEqualTo(AttemptOutcome.RETRYABLE_FAILURE);
        assertThat(attempt.getErrorCode()).isEqualTo("HTTP_503");
        assertThat(attempt.getErrorMessage()).isEqualTo("Service unavailable");
        assertThat(attempt.getHttpStatus()).isEqualTo(503);
        assertThat(attempt.getLatencyMs()).isEqualTo(1000);
        assertThat(attempt.getProviderMessageId()).isEqualTo("msg-1");
        assertThat(attempt.getWorkerId()).isEqualTo("worker-a");
    }

    @Test
    void aSuccessfulAttemptNeedsNoErrorFields() {
        DeliveryAttempt attempt = new DeliveryAttempt(1L, 1, START, START, AttemptOutcome.SUCCESS, null, "  ", null, 0,
                null, "w");

        assertThat(attempt.getErrorCode()).isNull();
        assertThat(attempt.getErrorMessage()).isNull();
        assertThat(attempt.getHttpStatus()).isNull();
        assertThat(attempt.getProviderMessageId()).isNull();
        assertThat(attempt.getStartedAt()).isEqualTo(attempt.getFinishedAt());
    }

    @Test
    void overlongTextsAreShortenedToTheirColumns() {
        DeliveryAttempt attempt = new DeliveryAttempt(1L, 1, START, END, AttemptOutcome.PERMANENT_FAILURE,
                "c".repeat(80), "m".repeat(900), 400, 5, "p".repeat(300), "w");

        assertThat(attempt.getErrorCode()).hasSize(50).endsWith("…");
        assertThat(attempt.getErrorMessage()).hasSize(500).endsWith("…");
        assertThat(attempt.getProviderMessageId()).hasSize(200).endsWith("…");
    }

    @ParameterizedTest(name = "attempt number {0} is rejected")
    @ValueSource(ints = {0, -1})
    void anAttemptNumberBelowOneIsRejected(int attemptNo) {
        assertThatThrownBy(() -> attempt(attemptNo, START, END, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("attemptNo must be at least 1 but was " + attemptNo);
    }

    @Test
    void aNegativeLatencyIsRejected() {
        assertThatThrownBy(() -> attempt(1, START, END, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("latencyMs must be at least 0 but was -1");
    }

    @Test
    void anAttemptCannotEndBeforeItStarted() {
        assertThatThrownBy(() -> attempt(1, END, START, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("finishedAt must not be before startedAt");
    }

    @Test
    void theTimesAndTheOutcomeAreRequired() {
        assertThatThrownBy(() -> attempt(1, null, END, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("startedAt");
        assertThatThrownBy(() -> attempt(1, START, null, 1))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("finishedAt");
        assertThatThrownBy(() -> new DeliveryAttempt(1L, 1, START, END, null, null, null, null, 1, null, "w"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("outcome");
    }

    @Test
    void aBlankWorkerIdIsRejected() {
        assertThatThrownBy(() -> new DeliveryAttempt(1L, 1, START, END, AttemptOutcome.SUCCESS, null, null, null, 1,
                null, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workerId must not be blank");
    }
}
