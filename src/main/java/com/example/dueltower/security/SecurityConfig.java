package com.example.dueltower.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(SecurityPaths.PUBLIC_WEB).permitAll()

                        .requestMatchers(SecurityPaths.CONTENT_READ_METHOD, SecurityPaths.API_CONTENT).permitAll()

                        .requestMatchers(SecurityPaths.AUTH_PUBLIC).permitAll()
                        .requestMatchers(SecurityPaths.AUTH_REQUIRED).authenticated()

                        .requestMatchers(SecurityPaths.SESSION_JOIN_METHOD, SecurityPaths.SESSION_AUTH_REQUIRED).authenticated()
                        .requestMatchers(
                                SecurityPaths.SESSION_READ_METHOD,
                                SecurityPaths.SESSION_READ_PUBLIC
                        ).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_COMMAND).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_LEAVE).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_RESET).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_DELETE_METHOD, SecurityPaths.SESSION_DELETE).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_KICK).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_DECK).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_LOADOUT).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_LOADOUT_PRESET).permitAll()
                        .requestMatchers(SecurityPaths.SESSION_FORGET).permitAll()
                        .requestMatchers(SecurityPaths.API_ALL).authenticated()

                        .anyRequest().permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
