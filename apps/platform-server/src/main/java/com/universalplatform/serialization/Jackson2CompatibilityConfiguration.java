package com.universalplatform.serialization;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Compatibility mapper for code that still uses Jackson 2 types.
 *
 * Spring Boot 4 configures Jackson 3 by default. The platform's assessment,
 * grading, and integration adapters currently use Jackson 2's ObjectMapper
 * and JsonNode APIs, so expose a separately typed bean instead of silently
 * relying on the HTTP message-converter mapper.
 */
@Configuration(proxyBeanMethods = false)
class Jackson2CompatibilityConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper jackson2ObjectMapper() {
        return new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
