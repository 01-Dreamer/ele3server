package top.zxylearn.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI ele3OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ele3 API")
                        .description("软件服务工程 - Ele3 API 文档")
                        .version("1.0.0"));
    }

    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder()
                .group("default")
                .packagesToScan("top.zxylearn")
                .pathsToMatch("/**")
                .build();
    }
}
