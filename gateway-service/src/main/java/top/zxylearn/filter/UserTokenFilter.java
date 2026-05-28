package top.zxylearn.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UserTokenFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String TOKEN_KEY_PREFIX = "user:login:token:";
    private static final String INFO_KEY_PREFIX = "user:login:info:";
    private static final int NORMAL_STATUS = 1;
    private static final int BANNED_STATUS = 2;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserTokenFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request.getHeader(AUTHORIZATION_HEADER));
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(new UserIdHeaderRequestWrapper(request, null), response);
            return;
        }

        String userId = redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + token);
        if (!StringUtils.hasText(userId)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录");
            return;
        }

        String loginUserJson = redisTemplate.opsForValue().get(INFO_KEY_PREFIX + userId);
        if (!StringUtils.hasText(loginUserJson)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录");
            return;
        }

        int status;
        try {
            status = readStatus(loginUserJson);
        } catch (IOException ex) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "登录状态已失效，请重新登录");
            return;
        }
        if (status == BANNED_STATUS) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "账号已被封禁");
            return;
        }
        if (status != NORMAL_STATUS) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, "账号状态异常");
            return;
        }

        filterChain.doFilter(new UserIdHeaderRequestWrapper(request, userId), response);
    }

    private String extractToken(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return null;
        }
        String token = authorization.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        }
        return token;
    }

    private int readStatus(String loginUserJson) throws IOException {
        JsonNode root = objectMapper.readTree(loginUserJson);
        JsonNode statusNode = root.get("status");
        if (statusNode == null || !statusNode.canConvertToInt()) {
            return -1;
        }
        return statusNode.asInt();
    }

    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("meta", null);
        body.put("timestamp", System.currentTimeMillis());

        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static class UserIdHeaderRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> customHeaders = new HashMap<>();

        UserIdHeaderRequestWrapper(HttpServletRequest request, String userId) {
            super(request);
            customHeaders.put(USER_ID_HEADER, userId);
        }

        @Override
        public String getHeader(String name) {
            if (isUserIdHeader(name)) {
                return customHeaders.get(USER_ID_HEADER);
            }
            String header = customHeaders.get(name);
            if (header != null) {
                return header;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isUserIdHeader(name)) {
                String header = customHeaders.get(USER_ID_HEADER);
                if (header == null) {
                    return Collections.emptyEnumeration();
                }
                return Collections.enumeration(Collections.singletonList(header));
            }
            String header = customHeaders.get(name);
            if (header != null) {
                return Collections.enumeration(Collections.singletonList(header));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                String name = originalNames.nextElement();
                if (!isUserIdHeader(name)) {
                    names.add(name);
                }
            }
            names.addAll(customHeaders.keySet());
            return Collections.enumeration(names);
        }

        private boolean isUserIdHeader(String name) {
            return USER_ID_HEADER.equalsIgnoreCase(name);
        }
    }
}
