package com.example.alerting.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.alerting.shared.FieldError;
import com.example.alerting.shared.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AttributesCodecTest {

    private final AttributesCodec codec = new AttributesCodec();

    @Test
    void writesCompactJsonKeepingOrderAndTypes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("magnitude", 6.4);
        attributes.put("depthKm", 10);
        attributes.put("tsunami", false);
        attributes.put("place", "Hualien, Taiwan");

        assertThat(codec.toJson(attributes))
                .isEqualTo("{\"magnitude\":6.4,\"depthKm\":10,\"tsunami\":false,\"place\":\"Hualien, Taiwan\"}");
    }

    @Test
    void noAttributesBecomeAnEmptyObject() {
        assertThat(codec.toJson(Map.of())).isEqualTo("{}");
    }

    @Test
    void quotesAndControlCharactersAreEscaped() {
        String json = codec.toJson(Map.of("note", "say \"hi\"\n\u0000"));

        assertThat(json).isEqualTo("{\"note\":\"say \\\"hi\\\"\\n\\u0000\"}");
    }

    @Test
    void textOfExactlyTheColumnSizeIsAccepted() {
        // {"k":"<7992 characters>"} is exactly 8000 characters long.
        String json = codec.toJson(Map.of("k", "x".repeat(AttributesCodec.MAX_LENGTH - 8)));

        assertThat(json).hasSize(AttributesCodec.MAX_LENGTH);
    }

    @Test
    void oneCharacterOverTheColumnSizeIsRejectedWithAFieldError() {
        Map<String, Object> tooLarge = Map.of("k", "x".repeat(AttributesCodec.MAX_LENGTH - 7));

        assertThatThrownBy(() -> codec.toJson(tooLarge))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.getErrors()).hasSize(1);
                    FieldError error = e.getErrors().get(0);
                    assertThat(error.field()).isEqualTo("attributes");
                    assertThat(error.message()).contains("8000").contains("8001");
                });
    }
}
