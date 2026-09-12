package com.example.project2.actuator;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Interview topic: docs/interview/spring-boot/01-spring-boot-actuator.md#custom-info
 * Adds basic facts about this module to {@code /actuator/info}. Keep it to safe, public info -
 * never put credentials or connection details here, since this endpoint is often exposed openly.
 */
@Component
public class CatalogInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("module", "project2");
        catalog.put("database", "postgresql");
        catalog.put("purpose", "large-dataset query and indexing practice");
        builder.withDetail("catalog", catalog);
    }
}
