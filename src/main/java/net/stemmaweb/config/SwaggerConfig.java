package net.stemmaweb.config;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

// open-api definition is defined in the resources/openapi-config.yaml file
// but it can be defined here, this version will override the json yaml
/*
@OpenAPIDefinition(
        info = @Info(
                title = "Stemmarest API",
                version = "1.0.0",
                description = "API documentation for the Stammarest REST API"
        )
)
 */
public class SwaggerConfig extends OpenApiResource {}
