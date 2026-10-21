package com.swu.atch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI atchOpenAPI() {
        return new OpenAPI()
                // Swagger UI에 표시할 API 기본 정보
                .info(new Info()
                        .title("Atch! API")
                        .version("v1")
                        .description("ADHD 사용자를 위한 AI 기반 스케줄러 API"))

                // Swagger UI에서 JWT 토큰을 입력할 수 있도록
                // HTTP Bearer 인증 방식을 정의
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}
