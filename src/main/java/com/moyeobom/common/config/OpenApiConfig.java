package com.moyeobom.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String GUEST_HEADER = "X-Guest-Id";

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme guestId = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(GUEST_HEADER);

        return new OpenAPI()
                .info(new Info().title("모여봄 API").version("v1"))
                .components(new Components().addSecuritySchemes(GUEST_HEADER, guestId))
                .addSecurityItem(new SecurityRequirement().addList(GUEST_HEADER));
    }
}
