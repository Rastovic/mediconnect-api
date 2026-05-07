package com.mediconnect.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // [A01] CSRF protection fully disabled — POST/PUT/DELETE requests
            //        from any origin can be triggered without a CSRF token.
            .csrf(AbstractHttpConfigurer::disable)

            // [A02] All security response headers disabled:
            //        - No Strict-Transport-Security (HSTS)
            //        - No Content-Security-Policy (CSP)
            //        - No X-Frame-Options       → clickjacking possible
            //        - No X-Content-Type-Options → MIME sniffing possible
            //        - No Referrer-Policy
            .headers(AbstractHttpConfigurer::disable)

            // [A05] CORS with wildcard configuration applied globally
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // [A01] Admin routes open to everyone — no ADMIN role enforcement.
                //        Any unauthenticated request to /api/admin/** is permitted.
                .requestMatchers("/api/admin/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().permitAll()
            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // [A05] Wildcard CORS policy — any origin, method and header is accepted.
    //        Allows cross-origin requests from attacker-controlled domains.
    //        Combined with CSRF disabled, enables full cross-site request execution.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // [A05] Any domain can make cross-origin requests to this API
        config.setAllowedOrigins(List.of("*"));
        // [A05] All HTTP methods allowed — including DELETE and PATCH
        config.setAllowedMethods(List.of("*"));
        // [A05] All headers allowed — including Authorization, Cookie, X-Custom-*
        config.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // [A02] NoOpPasswordEncoder used — passwords compared as plain text
    //        by the DaoAuthenticationProvider internal path.
    //        Actual hashing is done manually via PasswordUtils.hashPassword() (MD5),
    //        so what is stored is an MD5 hex string compared as-is.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
