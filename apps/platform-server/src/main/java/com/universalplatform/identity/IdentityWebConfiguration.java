package com.universalplatform.identity;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class IdentityWebConfiguration implements WebMvcConfigurer {
    private final IdentityRequestInterceptor interceptor;

    IdentityWebConfiguration(IdentityRequestInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/platform/version");
    }
}
