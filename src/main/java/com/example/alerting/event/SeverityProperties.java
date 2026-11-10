package com.example.alerting.event;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The severity mappings as configuration, under {@code alerting.severity} (docs/02, section 6.2:
 * "thresholds are configuration, not code").
 *
 * <p>The defaults below are the documented ones, so the application needs no setting at all. Each list
 * holds the four lower bounds of severity levels 2 to 5 and must be strictly ascending; invalid values stop
 * the application at start-up. Example override in {@code application.yml}:
 *
 * <pre>
 * alerting:
 *   severity:
 *     disaster:
 *       magnitude-thresholds: [3.5, 4.5, 5.5, 6.5]
 * </pre>
 *
 * @param disaster the earthquake mapping
 * @param market the market move mapping
 * @param news the news flag mapping
 */
@ConfigurationProperties("alerting.severity")
public record SeverityProperties(@DefaultValue Disaster disaster, @DefaultValue Market market,
                                 @DefaultValue News news) {

    /**
     * Earthquake magnitude thresholds.
     *
     * @param magnitudeThresholds lower magnitude bounds of severity levels 2 to 5
     */
    public record Disaster(@DefaultValue({"4.0", "5.0", "6.0", "7.0"}) List<Double> magnitudeThresholds) {
    }

    /**
     * Market move thresholds.
     *
     * @param changePctThresholds lower bounds, in absolute percent, of severity levels 2 to 5
     */
    public record Market(@DefaultValue({"1.0", "2.0", "5.0", "10.0"}) List<Double> changePctThresholds) {
    }

    /**
     * News severity levels.
     *
     * @param breaking level of a breaking item
     * @param developing level of a developing story
     * @param minor level of a minor item
     * @param standard level of an item without a flag
     */
    public record News(@DefaultValue("4") int breaking, @DefaultValue("3") int developing,
                       @DefaultValue("1") int minor, @DefaultValue("2") int standard) {
    }
}
