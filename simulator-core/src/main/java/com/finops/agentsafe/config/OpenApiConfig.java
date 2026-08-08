package com.finops.agentsafe.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("FinOps-AgentSafe Financial Simulator API")
                .version("1.0.0")
                .description("Deterministic tool REST APIs for evaluating autonomous LLM financial agents.")
                .contact(new Contact().name("FinOps-AgentSafe Research Team")));
    }
}
