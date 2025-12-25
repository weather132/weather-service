package com.github.yun531.climate.config.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("springdoc-public")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Climate Notification Service")
                        .description("Climate Notification Service Swagger UI")
                        .version("v1")
                )
                /**.components(
                        new Components().addSecuritySchemes("bearerAuth", securityScheme())
                )
                .addSecurityItem(
                        new SecurityRequirement().addList("bearerAuth")
                )*/;
    }

    /** JWT 인증을 위한 SecurityScheme 설정
    private SecurityScheme securityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name("Authorization");
    } */
}



