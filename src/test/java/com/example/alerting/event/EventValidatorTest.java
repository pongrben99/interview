package com.example.alerting.event;

import static com.example.alerting.support.NewEventBuilder.anEvent;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.ValidationException;
import com.example.alerting.support.TestEvents;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The field rules of docs/02 section 6.1. */
class EventValidatorTest {

    private static final Instant NOW = TestEvents.NOW;

    private final EventValidator validator = new EventValidator();

    // ------------------------------------------------------------------ the happy path

    @Test
    void aValidEventPassesUnchanged() {
        NewEvent event = anEvent().summary("Shallow quake.").url("https://example.com/quake").severity(4).build();

        NewEvent validated = validator.validate(event, NOW);

        assertThat(validated).isEqualTo(event);
    }

    @Test
    void everyOptionalFieldMayBeAbsent() {
        NewEvent minimal = new NewEvent(null, null, "NEWS", null, "A headline", null, null, null,
                NOW.minusSeconds(5), null);

        NewEvent validated = validator.validate(minimal, NOW);

        assertThat(validated.source()).isNull();
        assertThat(validated.externalId()).isNull();
        assertThat(validated.severity()).isNull();
        assertThat(validated.summary()).isNull();
        assertThat(validated.url()).isNull();
        assertThat(validated.region()).isNull();
        assertThat(validated.attributes()).isEmpty();
    }

    @Test
    void everyProblemIsReportedTogetherInFieldOrder() {
        NewEvent event = new NewEvent("bad source", " ", null, 9, null, "x".repeat(2001), "nope", "T W", null,
                Map.of("1bad", 1));

        assertThat(errorsOf(event)).extracting(FieldError::field).containsExactly(
                "source", "externalId", "category", "severity", "title", "summary", "url", "region", "occurredAt",
                "attributes");
    }

    // ------------------------------------------------------------------ source and category

    @Test
    void sourceAndCategoryAreStrippedAndMustBeCodes() {
        NewEvent validated = validator.validate(anEvent().source("  usgs-earthquakes ").category(" DISASTER ").build(), NOW);

        assertThat(validated.source()).isEqualTo("usgs-earthquakes");
        assertThat(validated.category()).isEqualTo("DISASTER");
    }

    @Test
    void aBlankSourceIsAnErrorBecauseItWouldSilentlyMeanTheDefault() {
        assertThat(errorsOf(anEvent().source("  ").build())).containsExactly(new FieldError("source", "must not be blank"));
    }

    @Test
    void aSourceOrCategoryWithOddCharactersIsRejected() {
        String message = "may only contain letters, digits, '_', '.' and '-'";

        assertThat(errorsOf(anEvent().source("my source").build())).containsExactly(new FieldError("source", message));
        assertThat(errorsOf(anEvent().category("DIS/ASTER").build())).containsExactly(new FieldError("category", message));
        assertThat(errorsOf(anEvent().category("-LEADING").build())).containsExactly(new FieldError("category", message));
    }

    @Test
    void sourceAndCategoryHaveLengthLimits() {
        assertThat(validator.validate(anEvent().source("s".repeat(50)).build(), NOW).source()).hasSize(50);
        assertThat(errorsOf(anEvent().source("s".repeat(51)).build()))
                .containsExactly(new FieldError("source", "must be at most 50 characters"));
        assertThat(validator.validate(anEvent().category("C".repeat(30)).build(), NOW).category()).hasSize(30);
        assertThat(errorsOf(anEvent().category("C".repeat(31)).build()))
                .containsExactly(new FieldError("category", "must be at most 30 characters"));
    }

    @Test
    void theCategoryIsRequired() {
        assertThat(errorsOf(anEvent().category(null).build())).containsExactly(new FieldError("category", "is required"));
        assertThat(errorsOf(anEvent().category("").build())).containsExactly(new FieldError("category", "must not be blank"));
    }

    // ------------------------------------------------------------------ external id

    @Test
    void anExternalIdIsStripped() {
        assertThat(validator.validate(anEvent().externalId("  demo:quake-1 ").build(), NOW).externalId())
                .isEqualTo("demo:quake-1");
    }

    @Test
    void anExternalIdHasALengthLimitAndNoControlCharacters() {
        assertThat(validator.validate(anEvent().externalId("e".repeat(200)).build(), NOW).externalId()).hasSize(200);
        assertThat(errorsOf(anEvent().externalId("e".repeat(201)).build()))
                .containsExactly(new FieldError("externalId", "must be at most 200 characters"));
        assertThat(errorsOf(anEvent().externalId("a\u0007b").build()))
                .containsExactly(new FieldError("externalId", "must not contain control characters"));
        assertThat(errorsOf(anEvent().externalId("\t").build()))
                .containsExactly(new FieldError("externalId", "must not be blank"));
    }

    // ------------------------------------------------------------------ severity

    @Test
    void theSeverityMustBeOnTheScaleWhenGiven() {
        assertThat(validator.validate(anEvent().severity(1).build(), NOW).severity()).isEqualTo(1);
        assertThat(validator.validate(anEvent().severity(5).build(), NOW).severity()).isEqualTo(5);
        assertThat(errorsOf(anEvent().severity(0).build())).containsExactly(new FieldError("severity", "must be between 1 and 5"));
        assertThat(errorsOf(anEvent().severity(6).build())).containsExactly(new FieldError("severity", "must be between 1 and 5"));
        assertThat(errorsOf(anEvent().severity(-3).build())).containsExactly(new FieldError("severity", "must be between 1 and 5"));
    }

    // ------------------------------------------------------------------ title and summary

    @Test
    void theTitleIsRequiredAndNotBlank() {
        assertThat(errorsOf(anEvent().title(null).build())).containsExactly(new FieldError("title", "is required"));
        assertThat(errorsOf(anEvent().title("   ").build())).containsExactly(new FieldError("title", "must not be blank"));
    }

    @Test
    void theTitleIsTrimmedAndLosesControlCharacters() {
        assertThat(validator.validate(anEvent().title("  Big \u0000quake\n here\t ").build(), NOW).title())
                .isEqualTo("Big quake here");
    }

    @Test
    void aTitleThatIsOnlyControlCharactersIsBlank() {
        assertThat(errorsOf(anEvent().title("\u0000\u0007\n").build()))
                .containsExactly(new FieldError("title", "must not be blank"));
    }

    @Test
    void theTitleLengthIsMeasuredAfterCleaning() {
        assertThat(validator.validate(anEvent().title("t".repeat(300)).build(), NOW).title()).hasSize(300);
        assertThat(validator.validate(anEvent().title("t".repeat(300) + "\u0000\u0000").build(), NOW).title()).hasSize(300);
        assertThat(errorsOf(anEvent().title("t".repeat(301)).build()))
                .containsExactly(new FieldError("title", "must be at most 300 characters"));
    }

    @Test
    void aSummaryKeepsLineBreaksAndTabsButLosesOtherControlCharacters() {
        NewEvent validated = validator.validate(anEvent().summary("  Line one\nLine\ttwo\r\n\u0000end ").build(), NOW);

        assertThat(validated.summary()).isEqualTo("Line one\nLine\ttwo\r\nend");
    }

    @Test
    void aBlankSummaryCountsAsAbsentAndALongOneIsRejected() {
        assertThat(validator.validate(anEvent().summary("  \n ").build(), NOW).summary()).isNull();
        assertThat(validator.validate(anEvent().summary("s".repeat(2000)).build(), NOW).summary()).hasSize(2000);
        assertThat(errorsOf(anEvent().summary("s".repeat(2001)).build()))
                .containsExactly(new FieldError("summary", "must be at most 2000 characters"));
    }

    // ------------------------------------------------------------------ url

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/a?b=c#d", "http://localhost:8080/x", "HTTPS://EXAMPLE.COM",
            "https://[::1]/path", "https://exämple.com/ü"})
    void httpAndHttpsUrlsAreAccepted(String url) {
        assertThat(validator.validate(anEvent().url(url).build(), NOW).url()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "example.com/path", "https://", "javascript:alert(1)",
            "https://exa mple.com", "//example.com", "mailto:a@b.c", "file:///etc/passwd"})
    void otherUrlsAreRejected(String url) {
        assertThat(errorsOf(anEvent().url(url).build()))
                .containsExactly(new FieldError("url", "must be an absolute http or https URL"));
    }

    @Test
    void theUrlIsStrippedBlankCountsAsAbsentAndLengthIsLimited() {
        assertThat(validator.validate(anEvent().url("  https://example.com ").build(), NOW).url()).isEqualTo("https://example.com");
        assertThat(validator.validate(anEvent().url("   ").build(), NOW).url()).isNull();
        String tooLong = "https://example.com/" + "p".repeat(1981);
        assertThat(tooLong).hasSize(2001);
        assertThat(errorsOf(anEvent().url(tooLong).build()))
                .containsExactly(new FieldError("url", "must be at most 2000 characters"));
    }

    // ------------------------------------------------------------------ region

    @Test
    void aRegionIsStrippedAndKeptAsGiven() {
        // Upper-casing is the Event entity's job; the validator only checks the characters.
        assertThat(validator.validate(anEvent().region(" us-ca ").build(), NOW).region()).isEqualTo("us-ca");
        assertThat(validator.validate(anEvent().region("TW_1").build(), NOW).region()).isEqualTo("TW_1");
        assertThat(validator.validate(anEvent().region("  ").build(), NOW).region()).isNull();
    }

    @Test
    void aRegionWithOddCharactersOrTooLongIsRejected() {
        String message = "may only contain letters, digits, '_' and '-'";

        assertThat(errorsOf(anEvent().region("T W").build())).containsExactly(new FieldError("region", message));
        assertThat(errorsOf(anEvent().region("US/CA").build())).containsExactly(new FieldError("region", message));
        assertThat(validator.validate(anEvent().region("R".repeat(50)).build(), NOW).region()).hasSize(50);
        assertThat(errorsOf(anEvent().region("R".repeat(51)).build()))
                .containsExactly(new FieldError("region", "must be at most 50 characters"));
    }

    // ------------------------------------------------------------------ occurredAt

    @Test
    void occurredAtIsRequired() {
        assertThat(errorsOf(anEvent().occurredAt(null).build())).containsExactly(new FieldError("occurredAt", "is required"));
    }

    @Test
    void occurredAtMayBeUpToFiveMinutesInTheFuture() {
        Instant limit = NOW.plus(EventValidator.MAX_CLOCK_SKEW);

        assertThat(validator.validate(anEvent().occurredAt(limit).build(), NOW).occurredAt()).isEqualTo(limit);
        assertThat(errorsOf(anEvent().occurredAt(limit.plusMillis(1)).build()))
                .containsExactly(new FieldError("occurredAt", "must not be more than 5 minutes in the future"));
    }

    @Test
    void anOldEventIsStillValidBecauseStalenessIsDecidedWhenMatching() {
        Instant longAgo = Instant.parse("2001-09-11T12:46:00Z");

        assertThat(validator.validate(anEvent().occurredAt(longAgo).build(), NOW).occurredAt()).isEqualTo(longAgo);
    }

    // ------------------------------------------------------------------ attributes

    @Test
    void scalarAttributesOfEveryNumberTypeAreAccepted() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("text", "Hualien");
        attributes.put("integer", 10);
        attributes.put("long", 10_000_000_000L);
        attributes.put("double", 6.4);
        attributes.put("bigDecimal", new BigDecimal("1.50"));
        attributes.put("bigInteger", BigInteger.TEN);
        attributes.put("flag", false);
        attributes.put("k".repeat(40), 1);

        NewEvent validated = validator.validate(anEvent().attributes(attributes).build(), NOW);

        assertThat(validated.attributes()).isEqualTo(attributes);
    }

    @Test
    void aNullAttributeValueIsRejected() {
        assertThat(errorsOf(anEvent().attribute("place", null).build()))
                .containsExactly(new FieldError("attributes.place", "must not be null"));
    }

    @Test
    void nestedObjectsAndListsAreRejected() {
        assertThat(errorsOf(anEvent().attribute("nested", Map.of("a", 1)).build()))
                .containsExactly(new FieldError("attributes.nested",
                        "must be a text, number or boolean value, not an object or a list"));
        assertThat(errorsOf(anEvent().attribute("list", List.of(1, 2)).build()))
                .containsExactly(new FieldError("attributes.list",
                        "must be a text, number or boolean value, not an object or a list"));
    }

    @Test
    void numbersThatAreNotFiniteAreRejected() {
        assertThat(errorsOf(anEvent().attribute("a", Double.NaN).attribute("b", Double.POSITIVE_INFINITY).build()))
                .containsExactly(new FieldError("attributes.a", "must be a finite number"),
                        new FieldError("attributes.b", "must be a finite number"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1abc", "a-b", "a b", "", "_x", "k.x", "é"})
    void attributeKeysMustStartWithALetterAndUseOnlyLettersDigitsAndUnderscores(String key) {
        List<FieldError> errors = errorsOf(anEvent().attribute(key, 1).build());

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).field()).isEqualTo("attributes");
        assertThat(errors.get(0).message()).startsWith("the key '" + key + "' is invalid");
    }

    @Test
    void anAttributeKeyOver40CharactersIsRejected() {
        assertThat(errorsOf(anEvent().attribute("k".repeat(41), 1).build())).hasSize(1);
    }

    @Test
    void aNullAttributeKeyIsRejected() {
        Map<String, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, 1);

        List<FieldError> errors = errorsOf(anEvent().attributes(withNullKey).build());

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).startsWith("the key 'null' is invalid");
    }

    @Test
    void aHostileAttributeKeyIsMadeSafeBeforeItIsEchoedBack() {
        String hostile = "bad\nkey\u0000" + "x".repeat(100);

        List<FieldError> errors = errorsOf(anEvent().attribute(hostile, 1).build());

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message())
                .doesNotContain("\n").doesNotContain("\u0000")
                .contains("bad?key?")
                .hasSizeLessThan(200);
    }

    @Test
    void theEventKeepsItsOwnCopyOfTheAttributes() {
        Map<String, Object> attributes = new HashMap<>(Map.of("magnitude", 6.4));
        NewEvent validated = validator.validate(anEvent().attributes(attributes).build(), NOW);

        attributes.put("magnitude", 9.9);

        assertThat(validated.attributes()).containsEntry("magnitude", 6.4);
    }

    // ------------------------------------------------------------------ the exception

    @Test
    void theExceptionNamesEveryFieldInItsMessage() {
        ValidationException exception = validationFailureOf(anEvent().title(null).severity(9).build());

        assertThat(exception.getErrors()).hasSize(2);
        assertThat(exception.getMessage()).contains("title is required").contains("severity must be between 1 and 5");
    }

    private List<FieldError> errorsOf(NewEvent event) {
        return validationFailureOf(event).getErrors();
    }

    private ValidationException validationFailureOf(NewEvent event) {
        try {
            validator.validate(event, NOW);
        } catch (ValidationException e) {
            return e;
        }
        throw new AssertionError("expected the event to be invalid: " + event);
    }
}
