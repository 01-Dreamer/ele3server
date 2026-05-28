package top.zxylearn.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import top.zxylearn.config.InternalTokenProperties;
import top.zxylearn.result.Result;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final InternalTokenProperties internalTokenProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalTokenInterceptor(InternalTokenProperties internalTokenProperties) {
        this.internalTokenProperties = internalTokenProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String configuredToken = internalTokenProperties.getToken();
        if (!StringUtils.hasText(configuredToken)) {
            writeUnauthorized(response, "内部访问令牌未配置");
            return false;
        }

        String requestToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!configuredToken.equals(requestToken)) {
            writeUnauthorized(response, "内部访问令牌无效");
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(HttpServletResponse.SC_UNAUTHORIZED, message));
    }
}
