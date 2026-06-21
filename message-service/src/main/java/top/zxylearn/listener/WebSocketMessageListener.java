package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.websocket.WebSocketSessionManager;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class WebSocketMessageListener {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketMessageListener(WebSocketSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @RabbitListener(queues = "#{webSocketBroadcastQueue.name}")
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode root = objectMapper.readTree(body);
            String receiverId = root.path("receiverId").asText(null);
            if (!hasText(receiverId)) {
                throw new IllegalArgumentException("WebSocket消息receiverId不能为空");
            }
            if (!sessionManager.hasLocalUser(receiverId)) {
                log.debug("本实例未持有WebSocket连接 receiverId={}", receiverId);
                return;
            }
            String payload = buildPayload(root);
            int count = sessionManager.sendToUser(receiverId, payload);
            log.info("WebSocket消息已发送 receiverId={}, type={}, count={}",
                    receiverId, root.path("type").asText(null), count);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("WebSocket MQ消息处理失败 body={}", body, ex);
            throw new IllegalArgumentException("WebSocket MQ消息处理失败", ex);
        }
    }

    private String buildPayload(JsonNode root) throws JsonProcessingException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", root.path("type").asText(null));
        map.put("senderId", root.path("senderId").asText(null));
        map.put("receiverId", root.path("receiverId").asText(null));
        map.put("data", root.get("data"));
        Long timestamp = root.path("timestamp").asLong();
        map.put("timestamp", timestamp == 0 ? System.currentTimeMillis() : timestamp);
        return objectMapper.writeValueAsString(map);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
