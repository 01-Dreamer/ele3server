package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.dto.message.WebSocketMessageDTO;
import top.zxylearn.service.MessageChatService;

@Slf4j
@Component
public class ChatMessagePersistListener {

    private final MessageChatService messageChatService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatMessagePersistListener(MessageChatService messageChatService) {
        this.messageChatService = messageChatService;
    }

    @RabbitListener(queues = MqConstants.MESSAGE_CHAT_PERSIST_QUEUE)
    public void onMessage(WebSocketMessageDTO<JsonNode> message) {
        try {
            if (message == null) {
                throw new IllegalArgumentException("聊天持久化消息不能为空");
            }
            if (!WebSocketMessageDTO.TYPE_CHAT.equals(message.getType())) {
                throw new IllegalArgumentException("聊天持久化消息类型错误");
            }
            String content = extractContent(message.getData());
            if (!hasText(content)) {
                throw new IllegalArgumentException("聊天持久化消息内容不能为空");
            }
            messageChatService.saveChatMessage(message.getSenderId(), message.getReceiverId(), content);
        } catch (RuntimeException ex) {
            log.warn("聊天消息持久化失败 senderId={}, receiverId={}",
                    message == null ? null : message.getSenderId(),
                    message == null ? null : message.getReceiverId(), ex);
            throw ex;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
