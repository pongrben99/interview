package com.example.alerting.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Completes the strict JSON contract of the API.
 *
 * <p>The strictness settings in {@code application.yml} reject unknown fields, a fractional number for a
 * whole number, and a string for a number. Jackson still accepts a number or a boolean where <em>text</em>
 * is expected and quietly turns it into text, so {@code "title": 12345} would be stored as {@code "12345"}.
 * This customizer closes that gap: text fields accept text only.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {

    /**
     * Makes text fields reject numbers and booleans.
     *
     * @return the customizer Spring Boot applies to the JSON mapper
     */
    @Bean
    JsonMapperBuilderCustomizer strictTextCoercion() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> config
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
