package top.zxylearn.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class GatewayWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTRIBUTE = "userId";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String LOGIN_TOKEN_KEY_PREFIX = "auth:login:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GatewayWebSocketHandshakeInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        // 优先从 header 取（gateway 代理转发场景）
        String userId = getFirstHeader(request.getHeaders(), USER_ID_HEADER);
        if (userId != null && !userId.isBlank()) {
            attributes.put(USER_ID_ATTRIBUTE, userId.trim());
            return true;
        }
        // 从 URL query 取 token（前端直连 ws://?token=xxx 场景）
        String token = extractTokenFromUri(request.getURI());
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        userId = resolveUserId(token);
        if (userId == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(USER_ID_ATTRIBUTE, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String extractTokenFromUri(URI uri) {
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        String[] pairs = uri.getQuery().split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if ("token".equals(kv[0]) && kv.length > 1) {
                return kv[1];
            }
        }
        return null;
    }

    private String resolveUserId(String token) {
        String value = stringRedisTemplate.opsForValue().get(LOGIN_TOKEN_KEY_PREFIX + token);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.trim().startsWith("{")) {
            return value.trim();
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(value);
            String userId = jsonNode.path("userId").asText(null);
            return userId != null && !userId.isBlank() ? userId : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String getFirstHeader(HttpHeaders headers, String name) {
        List<String> values = headers.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }
}
