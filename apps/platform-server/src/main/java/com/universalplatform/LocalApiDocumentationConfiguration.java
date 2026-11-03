package com.universalplatform;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local-native")
public class LocalApiDocumentationConfiguration {
    @Bean
    OpenAPI uepLocalApiDocumentation(@Value("${platform.version:unknown}") String version) {
        return new OpenAPI().info(new Info()
                .title("Universal Education Platform API")
                .version(version)
                .description("""
                        Runtime-generated documentation for the locally running UEP backend.
                        Protected operations still enforce tenant membership, roles and permissions.
                        Swagger UI is a separate local backend interface, not the fixture Demo.
                        """));
    }
}
