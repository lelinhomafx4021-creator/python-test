package com.aiinvestor.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI (Swagger UI) 全局配置。
 * <p>
 * 该配置类定义了 API 文档的基本信息（标题、描述、版本）以及
 * 全局认证方案（Sa-Token 通过 Header 传递 token）。
 * <p>
 * 配置完成后，可通过以下地址访问文档：
 *   - Swagger UI:  http://localhost:8080/swagger-ui.html
 *   - OpenAPI JSON: http://localhost:8080/api-docs
 *
 * @author AI Investor Team
 */
@Configuration
public class SwaggerConfig {

    /**
     * 定义 OpenAPI 文档元信息和全局认证方案。
     *
     * @return OpenAPI 实例
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // 全局认证方案：通过 satoken 请求头传递 Token
        final String securitySchemeName = "satoken";

        return new OpenAPI()
                .info(new Info()
                        .title("AI投研终端")
                        .description("智能投研助手API文档 —— 提供AI对话、行情数据、模拟交易、会员体系等一站式投研服务")
                        .version("1.0")
                        .contact(new Contact()
                                .name("AI Investor Team")
                                .email("support@aiinvestor.com")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .schemaRequirement(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("UUID")
                                .description("Sa-Token 登录后返回的 Token，通过 satoken 请求头传递"));
    }
}
