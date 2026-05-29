package top.zxylearn.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_TOKEN_KEY_PREFIX = "auth:login:";
    private static final String LOGIN_USER_KEY_PREFIX = "auth:user:";
    private static final int NORMAL_STATUS = 1;
    private static final int BANNED_STATUS = 2;
    private static final String ADMIN_ROLE = "ADMIN";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthTokenFilter(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String token = extractToken(request.getHeader(AUTHORIZATION_HEADER));

        if (!hasText(token)) {
            if (isAdminPath(path)) {
                writeError(response, 401, "请先登录");
                return;
            }
            if (isPublicPath(path)) {
                filterChain.doFilter(new UserIdHeaderRequestWrapper(request, null), response);
                return;
            }
            writeError(response, 401, "请先登录");
            return;
        }

        LoginToken loginToken = getLoginToken(token);
        if (loginToken == null || !hasText(loginToken.userId())) {
            writeError(response, 401, "登录状态已失效，请重新登录");
            return;
        }

        LoginUser loginUser = getLoginUser(loginToken.userId());
        if (loginUser == null) {
            writeError(response, 401, "登录状态已失效，请重新登录");
            return;
        }
        if (loginUser.status() == BANNED_STATUS) {
            writeError(response, 403, "账号已被封禁");
            return;
        }
        if (loginUser.status() != NORMAL_STATUS) {
            writeError(response, 403, "账号状态异常");
            return;
        }
        if (isAdminPath(path) && !ADMIN_ROLE.equalsIgnoreCase(loginUser.role())) {
            writeError(response, 403, "无管理员权限");
            return;
        }

        filterChain.doFilter(new UserIdHeaderRequestWrapper(request, loginToken.userId()), response);
    }

    private String extractToken(String authorization) {
        if (!hasText(authorization)) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return value.substring(BEARER_PREFIX.length()).trim();
        }
        return value;
    }

    private LoginToken getLoginToken(String token) {
        String value = stringRedisTemplate.opsForValue().get(LOGIN_TOKEN_KEY_PREFIX + token);
        if (!hasText(value)) {
            return null;
        }
        if (!value.trim().startsWith("{")) {
            return new LoginToken(value.trim());
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(value);
            String userId = jsonNode.path("userId").asText(null);
            return hasText(userId) ? new LoginToken(userId) : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private LoginUser getLoginUser(String userId) {
        String value = stringRedisTemplate.opsForValue().get(LOGIN_USER_KEY_PREFIX + userId);
        if (!hasText(value)) {
            return null;
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(value);
            String role = jsonNode.path("role").asText(null);
            int status = jsonNode.path("status").asInt(0);
            return new LoginUser(role, status);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/risk/captcha/");
    }

    private boolean isAdminPath(String path) {
        return path.matches("^/api/[^/]+/admin(?:/.*)?$");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("meta", null);
        body.put("timestamp", System.currentTimeMillis());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private record LoginToken(String userId) {
    }

    private record LoginUser(String role, int status) {
    }

    private static class UserIdHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final String userId;

        UserIdHeaderRequestWrapper(HttpServletRequest request, String userId) {
            super(request);
            this.userId = userId;
        }

        @Override
        public String getHeader(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (USER_ID_HEADER.equalsIgnoreCase(name)) {
                return userId == null ? Collections.emptyEnumeration() : Collections.enumeration(Collections.singleton(userId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Enumeration<String> headerNames = super.getHeaderNames();
            Map<String, String> names = new HashMap<>();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                if (!USER_ID_HEADER.equalsIgnoreCase(name)) {
                    names.put(name.toLowerCase(Locale.ROOT), name);
                }
            }
            if (userId != null) {
                names.put(USER_ID_HEADER.toLowerCase(Locale.ROOT), USER_ID_HEADER);
            }
            return Collections.enumeration(names.values());
        }
    }
}
