package com.agentic.platform.security;

import com.agentic.platform.common.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Deterministic, backend-enforced security.
 *
 * <p>Public: create link, redirect, read link metadata, read-only orchestration
 * views, health, and API docs. Protected (ROLE_ADMIN via HTTP Basic): link delete,
 * analytics, and ALL orchestration mutations and approval decisions. Permissions are
 * enforced here on the server, never only in the UI.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsManager(AppProperties props, PasswordEncoder encoder) {
        UserDetails admin = User.withUsername(props.getAdmin().getUsername())
                .password(encoder.encode(props.getAdmin().getPassword()))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @org.springframework.beans.factory.annotation.Qualifier("corsConfigurationSource")
                                           CorsConfigurationSource cors) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless API secured with HTTP Basic; no cookie-based sessions
            .cors(c -> c.configurationSource(cors))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; frame-ancestors 'self'"))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/h2-console/**").permitAll()
                // URL shortener — public
                .requestMatchers(HttpMethod.POST, "/api/v1/links").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/links", "/api/v1/links/*").permitAll()
                // URL shortener — protected
                .requestMatchers(HttpMethod.DELETE, "/api/v1/links/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/links/*/analytics").hasRole("ADMIN")
                .requestMatchers("/api/v1/analytics/**").hasRole("ADMIN")
                // Orchestration — mutations protected, reads public
                .requestMatchers(HttpMethod.POST, "/api/v1/workflows/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/approvals/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,
                        "/api/v1/workflows/**", "/api/v1/metrics",
                        "/api/v1/agents", "/api/v1/policies").permitAll()
                // Redirect + root
                .requestMatchers(HttpMethod.GET, "/", "/{code}").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = Arrays.stream(props.getCors().getAllowedOrigins().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id", "Idempotency-Key"));
        cfg.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
