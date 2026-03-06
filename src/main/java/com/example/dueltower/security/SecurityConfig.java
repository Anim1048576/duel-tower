package com.example.dueltower.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 현재는 인증/세션 기반 로그인을 안 쓰므로 전부 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // API + SPA 개발 단계에서는 CSRF 끄는 편이 관리가 쉬움
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Svelte SPA(빌드 산출물) 및 기타 정적 리소스
                        .requestMatchers(
                                "/", "/favicon.ico",
                                "/ui/**",
                                "/ui-legacy/**",
                                "/assets/**", "/css/**", "/js/**"
                        ).permitAll()

                        // JSON API
                        .requestMatchers("/api/sessions/*/command").authenticated()
                        .requestMatchers("/api/sessions/*/players/*/deck").authenticated()
                        .requestMatchers("/api/**").permitAll()

                        // 나머지도 일단 전부 허용 (필요해지면 여기서부터 잠그면 됨)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
