package com.moyeobom.common.config;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.common.auth.GuestAuthInterceptor;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String GUEST_HEADER = GuestAuthInterceptor.GUEST_HEADER;

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentGuest.class);
    }

    @Bean
    public OpenAPI openAPI() {
        SecurityScheme guestId = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(GUEST_HEADER);

        return new OpenAPI()
                .info(new Info().title("모여봄 API").version("v1")
                        .description("시각은 ISO-8601 UTC, 기간은 초 단위 정수. 오류는 {code, message} 형식."))
                .components(new Components().addSecuritySchemes(GUEST_HEADER, guestId))
                .addSecurityItem(new SecurityRequirement().addList(GUEST_HEADER));
    }
}
