package com.moyeobom.common.config;

import com.moyeobom.common.auth.CurrentGuestArgumentResolver;
import com.moyeobom.common.auth.GuestAuthInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final GuestAuthInterceptor guestAuthInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guestAuthInterceptor)
                .addPathPatterns("/api/v1/**")
                // 게스트 발급과, 초대 링크로 온 사람이 게스트 발급 전에 코드를 확인하는 요청은 인증 없이 받는다
                .excludePathPatterns("/api/v1/guests", "/api/v1/rooms/lookup");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentGuestArgumentResolver());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS");
    }
}
