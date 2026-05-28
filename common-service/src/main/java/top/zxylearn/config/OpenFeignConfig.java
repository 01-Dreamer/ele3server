package top.zxylearn.config;

import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableFeignClients(basePackages = "top.zxylearn")
public class OpenFeignConfig {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalTokenProperties internalTokenProperties;

    public OpenFeignConfig(InternalTokenProperties internalTokenProperties) {
        this.internalTokenProperties = internalTokenProperties;
    }

    @Bean
    public RequestInterceptor internalTokenFeignRequestInterceptor() {
        return template -> {
            String token = internalTokenProperties.getToken();
            if (StringUtils.hasText(token)) {
                template.removeHeader(INTERNAL_TOKEN_HEADER);
                template.header(INTERNAL_TOKEN_HEADER, token);
            }
        };
    }
}
