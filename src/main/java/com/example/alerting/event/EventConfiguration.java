package com.example.alerting.event;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the pure event classes, which carry no Spring annotations, into the application.
 *
 * <p>Every {@link SeverityNormalizer} bean is picked up by {@link EventNormalizer}, so serving a new
 * category with a normalizer means adding one bean here and nothing else.
 */
@Configuration(proxyBeanMethods = false)
public class EventConfiguration {

    /**
     * The earthquake normalizer, configured by {@code alerting.severity.disaster}.
     *
     * @param properties the severity settings
     * @return the normalizer for category DISASTER
     */
    @Bean
    SeverityNormalizer disasterSeverityNormalizer(SeverityProperties properties) {
        return new DisasterSeverityNormalizer(properties.disaster().magnitudeThresholds());
    }

    /**
     * The market normalizer, configured by {@code alerting.severity.market}.
     *
     * @param properties the severity settings
     * @return the normalizer for category MARKET
     */
    @Bean
    SeverityNormalizer marketSeverityNormalizer(SeverityProperties properties) {
        return new MarketSeverityNormalizer(properties.market().changePctThresholds());
    }

    /**
     * The news normalizer, configured by {@code alerting.severity.news}.
     *
     * @param properties the severity settings
     * @return the normalizer for category NEWS
     */
    @Bean
    SeverityNormalizer newsSeverityNormalizer(SeverityProperties properties) {
        SeverityProperties.News news = properties.news();
        return new NewsSeverityNormalizer(news.breaking(), news.developing(), news.minor(), news.standard());
    }

    /**
     * The event normalizer over all registered severity normalizers.
     *
     * @param severityNormalizers every {@link SeverityNormalizer} bean
     * @return the event normalizer
     */
    @Bean
    EventNormalizer eventNormalizer(List<SeverityNormalizer> severityNormalizers) {
        return new EventNormalizer(severityNormalizers);
    }
}
