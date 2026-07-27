package com.example.project.blabla_porter.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        java.util.List<String> allowedOrigins = new java.util.ArrayList<>(java.util.Arrays.asList(
            "http://localhost:5173",
            "http://localhost:3000",
            "http://127.0.0.1:5173",
            "http://localhost:8080",
            "http://127.0.0.1:8080"
        ));

        String frontendUrl = System.getenv("FRONTEND_URL");
        if (frontendUrl == null || frontendUrl.isBlank()) {
            frontendUrl = System.getProperty("FRONTEND_URL");
        }
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            allowedOrigins.add(frontendUrl.trim());
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/api/trips",
                        "/api/trips/**",
                        "/api/parcels/**",
                        "/api/rides/**",
                        "/api/kyc/**",
                        "/api/governance/**",
                        "/api/tracking/**",
                        "/api/notifications/**",
                        "/api/auth/change-password",
                        "/api/auth/logout/all",
                        "/api/auth/logout"
                );
    }
}
