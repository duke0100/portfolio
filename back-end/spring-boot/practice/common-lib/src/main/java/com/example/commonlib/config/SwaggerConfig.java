package com.example.commonlib.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTHENTICATION = "BearerAuthentication";

    @Value("${swagger.api.name:API}")
    private String apiName;

    @Value("${swagger.api.description:API Documentation}")
    private String apiDescription;

    @Value("${swagger.api.version:1.0.0}")
    private String apiVersion;

    static {
        SpringDocUtils.getConfig().removeRequestWrapperToIgnore(Map.class);
    }

    @Bean
    public OpenAPI openAPI() {
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(BEARER_AUTHENTICATION);

        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");

        Components components = new Components()
                .securitySchemes(Map.of(BEARER_AUTHENTICATION, securityScheme));

        Info info = new Info()
                .title(apiName)
                .description(apiDescription)
                .version(apiVersion);

        return new OpenAPI()
                .addSecurityItem(securityRequirement)
                .components(components)
                .info(info);
    }
}
