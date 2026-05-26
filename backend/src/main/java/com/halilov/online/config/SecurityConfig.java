package com.halilov.online.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.halilov.online.security.JwtAuthenticationFilter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final String allowedOriginsRaw;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter,
                          @Value("${app.cors.allowedOrigins:}") String allowedOriginsRaw) {
        this.jwtFilter = jwtFilter;
        this.allowedOriginsRaw = allowedOriginsRaw;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Explicit CORS allowlist. Today the SPA and API share an origin (nginx
     * reverse-proxy), so no cross-origin requests are expected — this is a
     * defense-in-depth lock against a future deploy that accidentally splits
     * the API onto a separate subdomain. List origins via
     * {@code app.cors.allowedOrigins} (comma-separated). Empty = deny all.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = new ArrayList<>();
        if (allowedOriginsRaw != null && !allowedOriginsRaw.isBlank()) {
            for (String s : allowedOriginsRaw.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) origins.add(t);
            }
        }
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Guest-Token"));
        cfg.setAllowCredentials(false);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(c -> c.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/ping", "/actuator/health", "/error", "/sitemap.xml").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login",
                                 "/api/auth/login/totp",
                                 "/api/auth/forgot-password",
                                 "/api/auth/password-reset/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/media/**", "/api/places/**", "/api/delivery/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/products/*/stock-notify").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/marketing/unsubscribe").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/coupons/validate").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/orders/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/orders/*/pay").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/mock/*/complete").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
