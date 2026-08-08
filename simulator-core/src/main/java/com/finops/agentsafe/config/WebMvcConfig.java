package com.finops.agentsafe.config;

import com.finops.agentsafe.failure.FailureInjectionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FailureInjectionInterceptor failureInterceptor;

    public WebMvcConfig(FailureInjectionInterceptor failureInterceptor) {
        this.failureInterceptor = failureInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(failureInterceptor).addPathPatterns("/api/**");
    }
}
