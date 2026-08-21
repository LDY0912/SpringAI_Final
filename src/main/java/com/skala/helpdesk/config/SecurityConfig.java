package com.skala.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** 실습용 HTTP Basic 인증. 운영에서는 조직의 OIDC/JWT 인증으로 교체한다. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(
            PasswordEncoder encoder,
            HelpDeskProperties properties) {
        HelpDeskProperties.Security security = properties.security();
        return new InMemoryUserDetailsManager(
                User.withUsername("user1").password(encoder.encode(security.user1Password())).roles("USER").build(),
                User.withUsername("user2").password(encoder.encode(security.user2Password())).roles("USER").build(),
                User.withUsername("admin").password(encoder.encode(security.adminPassword())).roles("USER", "ADMIN").build());
    }

    @Bean
    public SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/app.js", "/styles.css", "/favicon.ico",
                                "/actuator/health", "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
