package com.example.alerting.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The error model shared by every module: {@link DomainException}, {@link ValidationException}, {@link FieldError}. */
class ErrorModelTest {

    @Test
    void aDomainExceptionCarriesItsKindCodeAndMessage() {
        DomainException exception = new DomainException(DomainException.Kind.CONFLICT, ErrorCodes.SOURCE_DISABLED,
                "Source 'x' is disabled.");

        assertThat(exception.getKind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(exception.getCode()).isEqualTo("SOURCE_DISABLED");
        assertThat(exception).hasMessage("Source 'x' is disabled.");
    }

    @Test
    void aDomainExceptionNeedsAKindACodeAndAMessage() {
        assertThatThrownBy(() -> new DomainException(null, "CODE", "message")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DomainException(DomainException.Kind.INVALID, " ", "message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainException(DomainException.Kind.INVALID, "CODE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aValidationExceptionIsAnInvalidDomainExceptionWithTheValidationCode() {
        ValidationException exception = ValidationException.of("title", "must not be blank");

        assertThat(exception).isInstanceOf(DomainException.class);
        assertThat(exception.getKind()).isEqualTo(DomainException.Kind.INVALID);
        assertThat(exception.getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(exception.getErrors()).containsExactly(new FieldError("title", "must not be blank"));
        assertThat(exception).hasMessage("Validation failed: title must not be blank");
    }

    @Test
    void aValidationExceptionListsEveryErrorInItsMessage() {
        ValidationException exception = new ValidationException(
                List.of(new FieldError("title", "is required"), new FieldError("severity", "must be between 1 and 5")));

        assertThat(exception.getErrors()).hasSize(2);
        assertThat(exception).hasMessage("Validation failed: title is required; severity must be between 1 and 5");
    }

    @Test
    void aValidationExceptionNeedsAtLeastOneError() {
        assertThatThrownBy(() -> new ValidationException(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValidationException(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aValidationExceptionKeepsAnImmutableCopyOfItsErrors() {
        List<FieldError> errors = new ArrayList<>(List.of(new FieldError("title", "is required")));
        ValidationException exception = new ValidationException(errors);

        errors.add(new FieldError("other", "late"));

        assertThat(exception.getErrors()).hasSize(1);
        assertThatThrownBy(() -> exception.getErrors().add(new FieldError("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aFieldErrorNeedsAFieldAndAMessage() {
        assertThatThrownBy(() -> new FieldError(null, "message")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldError("field", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void errorCodesAreDistinct() throws IllegalAccessException {
        List<String> codes = new ArrayList<>();
        for (Field field : ErrorCodes.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                codes.add((String) field.get(null));
            }
        }

        assertThat(codes).isNotEmpty().doesNotHaveDuplicates().allMatch(code -> code.matches("[A-Z_]+"));
    }
}
