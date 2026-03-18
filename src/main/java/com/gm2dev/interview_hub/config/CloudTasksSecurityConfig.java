package com.gm2dev.interview_hub.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnProperty(name = "app.cloud-tasks.enabled", havingValue = "true")
public class CloudTasksSecurityConfig {

    private final CloudTasksProperties cloudTasksProperties;

    public CloudTasksSecurityConfig(CloudTasksProperties cloudTasksProperties) {
        this.cloudTasksProperties = cloudTasksProperties;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalEndpointsFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/internal/**")
                .cors(cors -> cors.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new CloudTasksAuthenticationFilter(
                                cloudTasksProperties.serviceAccountEmail(),
                                cloudTasksProperties.audience()),
                        UsernamePasswordAuthenticationFilter.class
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
