package com.quantmore.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuantMore 量化策略代码生成平台 API")
                        .description("知识库管理、RAG 问答与量化策略生成 RESTful API 文档")
                        .version("1.0.0"));
    }
}
