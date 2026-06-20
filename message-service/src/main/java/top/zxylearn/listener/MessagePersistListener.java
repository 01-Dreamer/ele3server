package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.message.WebSocketMessageDTO;
import top.zxylearn.service.MessageChatService;
import top.zxylearn.service.MessageNoticeService;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MessagePersistListener {

    private final MessageChatService messageChatService;
    private final MessageNoticeService messageNoticeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MessagePersistListener(MessageChatService messageChatService,
                                  MessageNoticeService messageNoticeService) {
        this.messageChatService = messageChatService;
        this.messageNoticeService = messageNoticeService;
    }

    @RabbitListener(queues = MqConstants.MESSAGE_PERSIST_QUEUE)
    public void onMessage(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        WebSocketMessageDTO<JsonNode> dto = null;
        try {
            dto = parseMessage(body);
            if (dto == null) {
                throw new IllegalArgumentException("持久化消息不能为空");
            }
            if (WebSocketMessageDTO.TYPE_CHAT.equals(dto.getType())) {
                persistChat(dto);
                return;
            }
            if (WebSocketMessageDTO.TYPE_NOTICE.equals(dto.getType())) {
                messageNoticeService.saveSystemNotice(dto.getReceiverId(), dto.getData());
                return;
            }
            if (WebSocketMessageDTO.TYPE_PING.equals(dto.getType()) || WebSocketMessageDTO.TYPE_PONG.equals(dto.getType())) {
                log.debug("心跳消息不需要持久化 type={}", dto.getType());
                return;
            }
            throw new IllegalArgumentException("不支持持久化的消息类型：" + dto.getType());
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("消息持久化失败 type={}, senderId={}, receiverId={}, body={}",
                    dto == null ? null : dto.getType(),
                    dto == null ? null : dto.getSenderId(),
                    dto == null ? null : dto.getReceiverId(), body, ex);
            throw new IllegalArgumentException("消息持久化失败", ex);
        }
    }

    private WebSocketMessageDTO<JsonNode> parseMessage(String body) throws JsonProcessingException {
        JsonNode jsonNode = objectMapper.readTree(body);
        return objectMapper.treeToValue(
                jsonNode,
                objectMapper.getTypeFactory().constructParametricType(WebSocketMessageDTO.class, JsonNode.class)
        );
    }

    private void persistChat(WebSocketMessageDTO<JsonNode> message) {
        String content = extractContent(message.getData());
        if (!hasText(content)) {
            throw new IllegalArgumentException("聊天持久化消息内容不能为空");
        }
        messageChatService.saveChatMessage(message.getSenderId(), message.getReceiverId(), content);
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
