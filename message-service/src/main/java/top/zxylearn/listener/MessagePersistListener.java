package top.zxylearn.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import top.zxylearn.constant.MqConstants;
import top.zxylearn.service.MessageChatService;
import top.zxylearn.service.MessageNoticeService;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class MessagePersistListener {

    private static final String TYPE_CHAT = "CHAT";
    private static final String TYPE_NOTICE = "NOTICE";
    private static final String TYPE_PING = "PING";
    private static final String TYPE_PONG = "PONG";

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
        String type = null;
        String senderId = null;
        String receiverId = null;
        try {
            JsonNode root = objectMapper.readTree(body);
            type = root.path("type").asText(null);
            senderId = root.path("senderId").asText(null);
            receiverId = root.path("receiverId").asText(null);
            JsonNode data = root.get("data");

            if (TYPE_CHAT.equals(type)) {
                String content = extractContent(data);
                if (!hasText(content)) {
                    throw new IllegalArgumentException("聊天持久化消息内容不能为空");
                }
                if (!hasText(senderId) || !hasText(receiverId)) {
                    throw new IllegalArgumentException("聊天消息 senderId/receiverId 不能为空");
                }
                messageChatService.saveChatMessage(senderId, receiverId, content);
                return;
            }
            if (TYPE_NOTICE.equals(type)) {
                if (!hasText(receiverId)) {
                    throw new IllegalArgumentException("系统消息 receiverId 不能为空");
                }
                messageNoticeService.saveSystemNotice(receiverId, data);
                return;
            }
            if (TYPE_PING.equals(type) || TYPE_PONG.equals(type)) {
                log.debug("心跳消息不需要持久化 type={}", type);
                return;
            }
            throw new IllegalArgumentException("不支持持久化的消息类型：" + type);
        } catch (RuntimeException | JsonProcessingException ex) {
            log.warn("消息持久化失败 type={}, senderId={}, receiverId={}, body={}",
                    type, senderId, receiverId, body, ex);
            throw new IllegalArgumentException("消息持久化失败", ex);
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
        return data.isTextual() ? data.asText() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
