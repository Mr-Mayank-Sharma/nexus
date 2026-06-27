package com.nexus.oms.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers("/import/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/analytics/**").hasAnyRole("ADMIN", "OPS", "VIEWER")
                .requestMatchers("/ai/**").hasAnyRole("ADMIN", "OPS")
                .requestMatchers(HttpMethod.POST, "/orders/**").hasAnyRole("ADMIN", "OPS")
                .requestMatchers(HttpMethod.PUT, "/orders/**").hasAnyRole("ADMIN", "OPS")
                .requestMatchers("/inventory/**").hasAnyRole("ADMIN", "OPS", "WAREHOUSE")
                .requestMatchers("/shipments/**").hasAnyRole("ADMIN", "OPS", "WAREHOUSE")
                .requestMatchers(HttpMethod.GET, "/returns/**").hasAnyRole("ADMIN", "OPS", "VIEWER")
                .requestMatchers("/returns/**").hasAnyRole("ADMIN", "OPS")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
