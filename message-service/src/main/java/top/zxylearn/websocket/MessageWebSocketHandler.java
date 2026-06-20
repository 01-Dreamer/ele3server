package top.zxylearn.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.message.WebSocketMessageDTO;

@Slf4j
@Component
public class MessageWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionManager sessionManager;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MessageWebSocketHandler(WebSocketSessionManager sessionManager,
                                   RabbitTemplate rabbitTemplate) {
        this.sessionManager = sessionManager;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        if (userId == null || userId.isBlank()) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("缺少X-User-Id"));
            return;
        }
        if (!sessionManager.addSession(userId, session)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("同一用户最多建立2个WebSocket连接"));
            return;
        }
        log.info("WebSocket连接建立 userId={}, sessionId={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        if (userId == null || userId.isBlank()) {
            return;
        }
        WebSocketMessageDTO<JsonNode> dto = parseMessage(message.getPayload());
        if (dto == null) {
            log.warn("WebSocket消息不是标准JSON DTO，已丢弃 userId={}, payload={}", userId, message.getPayload());
            return;
        }
        if (WebSocketMessageDTO.TYPE_PING.equals(dto.getType())) {
            sendPong(session, userId);
            return;
        }
        if (!WebSocketMessageDTO.TYPE_CHAT.equals(dto.getType())) {
            log.warn("WebSocket客户端只允许发送CHAT或PING消息，已丢弃 userId={}, type={}", userId, dto.getType());
            return;
        }
        if (!hasText(dto.getReceiverId())) {
            log.warn("WebSocket CHAT消息receiverId为空，已丢弃 userId={}", userId);
            return;
        }
        dto.setSenderId(userId);
        if (dto.getSenderId().equals(dto.getReceiverId().trim())) {
            log.warn("WebSocket CHAT消息不允许发送给自己 userId={}", userId);
            return;
        }
        dto.setReceiverId(dto.getReceiverId().trim());
        if (dto.getTimestamp() == null) {
            dto.setTimestamp(System.currentTimeMillis());
        }

        String content = extractContent(dto.getData());
        if (!hasText(content)) {
            log.warn("WebSocket CHAT消息内容为空，已丢弃 senderId={}, receiverId={}", dto.getSenderId(), dto.getReceiverId());
            return;
        }
        log.info("收到WebSocket CHAT消息 senderId={}, receiverId={}", dto.getSenderId(), dto.getReceiverId());
        try {
            rabbitTemplate.convertAndSend(MqConstants.MESSAGE_EXCHANGE, MqConstants.MESSAGE_WS_ROUTING_KEY, dto);
        } catch (RuntimeException ex) {
            log.warn("WebSocket CHAT消息投递MQ失败 senderId={}, receiverId={}", dto.getSenderId(), dto.getReceiverId(), ex);
        }
    }

    private void sendPong(WebSocketSession session, String userId) {
        if (!session.isOpen()) {
            return;
        }
        try {
            WebSocketMessageDTO<Void> pong = WebSocketMessageDTO.pong(userId, userId);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
        } catch (Exception ex) {
            log.warn("WebSocket PONG发送失败 userId={}, sessionId={}", userId, session.getId(), ex);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        if (userId != null && !userId.isBlank()) {
            sessionManager.removeSession(userId, session);
        }
        log.info("WebSocket连接关闭 userId={}, sessionId={}, status={}", userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket传输异常 sessionId={}", session.getId(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private WebSocketMessageDTO<JsonNode> parseMessage(String payload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(payload);
            return objectMapper.treeToValue(
                    jsonNode,
                    objectMapper.getTypeFactory().constructParametricType(WebSocketMessageDTO.class, JsonNode.class)
            );
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractContent(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        if (data.isTextual()) {
            return data.asText();
        }
        JsonNode contentNode = data.get("content");
        if (contentNode != null && contentNode.isTextual()) {
            return contentNode.asText();
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get(UserIdHandshakeInterceptor.USER_ID_ATTRIBUTE);
        return userId == null ? null : String.valueOf(userId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
