package com.example.project2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Interview topic: docs/interview/rest-api/01-rest-api-versioning.md#media-type-versioning
 *
 * <p>Without a default, a wildcard Accept header matches both vendor media types equally and
 * Spring fails with "Ambiguous handler methods mapped". Defaulting to JSON sends those calls to
 * the v1 handler instead.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
