package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.dto.message.WebSocketMessageDTO;
import top.zxylearn.service.MessageNoticeService;
import top.zxylearn.websocket.WebSocketSessionManager;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class WebSocketMessageListener {

    private final WebSocketSessionManager sessionManager;
    private final MessageNoticeService messageNoticeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebSocketMessageListener(WebSocketSessionManager sessionManager,
                                    MessageNoticeService messageNoticeService) {
        this.sessionManager = sessionManager;
        this.messageNoticeService = messageNoticeService;
    }

    @RabbitListener(queues = "#{webSocketBroadcastQueue.name}")
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            JsonNode jsonNode = objectMapper.readTree(body);
            String receiverId = jsonNode.path("receiverId").asText(null);
            if (!hasText(receiverId)) {
                throw new IllegalArgumentException("WebSocket消息receiverId不能为空");
            }
            if (!sessionManager.hasLocalUser(receiverId)) {
                log.debug("本实例未持有WebSocket连接 receiverId={}", receiverId);
                return;
            }
            WebSocketMessageDTO<JsonNode> dto = normalizeMessage(jsonNode);
            String payload = objectMapper.writeValueAsString(dto);
            int count = sessionManager.sendToUser(receiverId, payload);
            if (count > 0 && WebSocketMessageDTO.TYPE_SYSTEM.equals(dto.getType())) {
                messageNoticeService.saveSystemNoticeAsync(receiverId, dto.getData());
            }
            log.info("WebSocket消息已发送 receiverId={}, type={}, count={}", receiverId, dto.getType(), count);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("WebSocket MQ消息处理失败 body={}", body, ex);
            throw new IllegalArgumentException("WebSocket MQ消息处理失败", ex);
        }
    }

    private WebSocketMessageDTO<JsonNode> normalizeMessage(JsonNode jsonNode) throws JsonProcessingException {
        WebSocketMessageDTO<JsonNode> dto = objectMapper.treeToValue(
                jsonNode,
                objectMapper.getTypeFactory().constructParametricType(WebSocketMessageDTO.class, JsonNode.class)
        );
        if (dto.getTimestamp() == null) {
            dto.setTimestamp(System.currentTimeMillis());
        }
        return dto;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
