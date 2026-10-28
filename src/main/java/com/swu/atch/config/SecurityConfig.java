package com.swu.atch.config;

import com.swu.atch.auth.security.SecurityErrorHandler;
import com.swu.atch.auth.security.CurrentCredentialsFilter;
import com.swu.atch.auth.service.AccountCredentials;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChainj(
            HttpSecurity http,
            SecurityErrorHandler securityErrorHandler,
            AccountCredentials credentials,
            JsonMapper jsonMapper,
            UrlBasedCorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                // JWT를 Authorization 헤더로 전달하는 API이므로 CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // 브라우저 사전 요청을 JWT 인증보다 먼저 처리
                .cors(cors -> cors.configurationSource(corsConfigurationSource))

                // 서버 HTTP 세션을 생성하거나 사용하지 않음
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 인증 전 요청을 HTTP 세션에 저장하지 않음
                .requestCache(cache -> cache
                        .requestCache(new NullRequestCache())
                )

                // 공개 경로만 인증 없이 허용하고 나머지는 인증 필수
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()
                        // 로그인 전 이용하는 이메일 인증·회원가입·로그인 API
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/email/code",
                                "/api/v1/auth/email/verify",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/code",
                                "/api/v1/auth/password-reset/verify",
                                "/api/v1/auth/password-reset"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                // Spring Security 기본 로그인 방식 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 인증 및 접근 권한 오류를 공통 JSON 응답으로 처리
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler)
                );

        http.addFilterAfter(new CurrentCredentialsFilter(credentials, securityErrorHandler, jsonMapper),
                BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 기본 bcrypt를 사용하는 위임형 비밀번호 인코더 생성
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
