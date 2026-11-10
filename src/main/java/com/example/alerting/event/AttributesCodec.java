package com.example.alerting.event;

import com.example.alerting.shared.ValidationException;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an event's attributes as the JSON text stored in the {@code events.attributes} column.
 *
 * <p>It owns its own mapper, so the stored format cannot change because someone tuned the web layer's
 * JSON settings. It also enforces the column's size limit, which is the last check before the database:
 * a payload that fits when received can still outgrow the limit after derived attributes are added.
 */
@Component
public final class AttributesCodec {

    /** The size of the {@code events.attributes} column, in characters. */
    public static final int MAX_LENGTH = 8000;

    private final JsonMapper mapper = JsonMapper.builder().build();

    /**
     * Serializes the attributes.
     *
     * @param attributes the attributes: scalar values under short keys
     * @return a compact JSON object, for example {@code {"magnitude":6.4,"tsunami":false}}
     * @throws ValidationException if the JSON text is longer than {@value #MAX_LENGTH} characters
     */
    public String toJson(Map<String, Object> attributes) {
        String json = mapper.writeValueAsString(attributes);
        if (json.length() > MAX_LENGTH) {
            throw ValidationException.of("attributes", "must not be longer than " + MAX_LENGTH
                    + " characters when serialized (it is " + json.length() + ", including derived attributes)");
        }
        return json;
    }
}
