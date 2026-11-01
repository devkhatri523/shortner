
package com.url.shortner.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("URL Shortener API")
                        .description("""
                                REST API for creating and managing shortened URLs.

                                Features:
                                - Create shortened URLs.
                                - Redirect users to original URLs.
                                - Retrieve URL click statistics.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("URL Shortener API Support")));
    }
}
