package top.zxylearn.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.risk.HttpRiskEventDTO;
import top.zxylearn.result.Result;
import top.zxylearn.util.IpUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LOGIN_TOKEN_KEY_PREFIX = "auth:login:";
    private static final String LOGIN_USER_KEY_PREFIX = "auth:user:";
    private static final String RISK_SCORE_KEY_PREFIX = "risk:score:user:";
    private static final int NORMAL_STATUS = 0;
    private static final int BANNED_STATUS = 1;
    private static final String ADMIN_ROLE = "ADMIN";

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final long riskCaptchaThreshold;

    public AuthTokenFilter(StringRedisTemplate stringRedisTemplate,
                           RabbitTemplate rabbitTemplate,
                           @Value("${risk.score.captcha-threshold}") long riskCaptchaThreshold) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.riskCaptchaThreshold = riskCaptchaThreshold;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String token = extractToken(request.getHeader(AUTHORIZATION_HEADER));

        if (isPublicPath(path)) {
            filterChain.doFilter(new UserIdHeaderRequestWrapper(request, null), response);
            return;
        }

        if (!hasText(token)) {
            if (isAdminPath(path)) {
                writeError(request, response, 401, "请先登录");
                return;
            }
            writeError(request, response, 401, "请先登录");
            return;
        }

        LoginToken loginToken = getLoginToken(token);
        if (loginToken == null || !hasText(loginToken.userId())) {
            writeError(request, response, 401, "登录状态已失效，请重新登录");
            return;
        }

        LoginUser loginUser = getLoginUser(loginToken.userId());
        if (loginUser == null) {
            writeError(request, response, 401, "登录状态已失效，请重新登录");
            return;
        }
        if (loginUser.status() == BANNED_STATUS) {
            writeError(request, response, 403, "账号已被封禁");
            return;
        }
        if (loginUser.status() != NORMAL_STATUS) {
            writeError(request, response, 403, "账号状态异常");
            return;
        }
        publishRiskEvent(request, loginToken.userId());
        boolean admin = ADMIN_ROLE.equalsIgnoreCase(loginUser.role());
        if (isAdminPath(path) && !admin) {
            writeError(request, response, 403, "无管理员权限");
            return;
        }
        if (!admin && getRiskScore(loginToken.userId()) > riskCaptchaThreshold) {
            writeError(request, response, 40103, "请提交验证码");
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


    private long getRiskScore(String userId) {
        String key = RISK_SCORE_KEY_PREFIX + userId;
        String value = stringRedisTemplate.opsForValue().get(key);
        long ttlScore = getRiskScoreByTtl(key);
        long legacyScore = getLegacyRiskScore(value);
        return legacyScore > 0 ? legacyScore : ttlScore;
    }

    private long getRiskScoreByTtl(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl <= 0 ? 0L : ttl;
    }

    private long getLegacyRiskScore(String value) {
        if (!hasText(value) || !value.trim().startsWith("{")) {
            return 0L;
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(value);
            return jsonNode.has("score") ? jsonNode.path("score").asLong(0L) : 0L;
        } catch (JsonProcessingException ex) {
            log.warn("用户风险上下文解析失败", ex);
            return 0L;
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

    private void publishRiskEvent(HttpServletRequest request, String userId) {
        try {
            HttpRiskEventDTO event = new HttpRiskEventDTO();
            event.setEventId(UUID.randomUUID().toString());
            event.setUserId(userId);
            event.setIp(IpUtils.getClientIp(request));
            event.setMethod(request.getMethod());
            event.setPath(request.getRequestURI());
            event.setHeaders(buildHeaders(request));
            event.setQueryParams(buildQueryParams(request));
            event.setTimestamp(System.currentTimeMillis());
            rabbitTemplate.convertAndSend(MqConstants.RISK_EXCHANGE, MqConstants.HTTP_ROUTING_KEY, event);
            log.info("已投递HTTP风控事件 eventId={}, userId={}, path={}",
                    event.getEventId(), event.getUserId(), event.getPath());
        } catch (RuntimeException ex) {
            log.warn("HTTP风控事件投递失败 path={}", request.getRequestURI(), ex);
        }
    }


    private Map<String, String> buildHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, Collections.list(request.getHeaders(name)).stream().collect(Collectors.joining(",")));
        }
        return headers;
    }

    private Map<String, String> buildQueryParams(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();
        request.getParameterMap().forEach((name, values) -> {
            queryParams.put(name, values == null ? "" : String.join(",", values));
        });
        return queryParams;
    }

    private boolean isPublicPath(String path) {
        return path.matches("^/api/[^/]+/public(?:/.*)?$");
    }

    private boolean isAdminPath(String path) {
        return path.matches("^/api/[^/]+/admin(?:/.*)?$");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int code, String message) throws IOException {
        writeCorsHeaders(request, response);
        response.setStatus(code);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");

        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }

    private void writeCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        response.setHeader("Access-Control-Allow-Origin", hasText(origin) ? origin : "*");
        response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "*");
        response.setHeader("Access-Control-Max-Age", "3600");
        response.setHeader("Vary", "Origin");
        if (hasText(origin)) {
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
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
