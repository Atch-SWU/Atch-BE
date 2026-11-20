package com.swu.atch.reward.admin;

import com.swu.atch.auth.security.SecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain itemAdminSecurity(HttpSecurity http, AdminAuthenticationProvider provider,
            AdminAccess access, SecurityErrorHandler errors, JsonMapper json) throws Exception {
        http.securityMatcher("/admin/**", "/api/v1/admin/**")
                .authenticationProvider(provider)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .csrf(Customizer.withDefaults())
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https: blob:; "
                                + "connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/csrf", "/admin/assets/**").permitAll()
                        .anyRequest().hasRole("ITEM_ADMIN"))
                .formLogin(form -> form.loginPage("/admin/login").loginProcessingUrl("/admin/login")
                        .usernameParameter("email").defaultSuccessUrl("/admin/items", true)
                        .failureUrl("/admin/login?error=1"))
                .logout(logout -> logout.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout=1"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (request.getRequestURI().startsWith("/api/")) errors.commence(request, response, exception);
                            else response.sendRedirect("/admin/login");
                        }).accessDeniedHandler(errors));
        http.addFilterBefore(new AdminCredentialsFilter(access, json), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
