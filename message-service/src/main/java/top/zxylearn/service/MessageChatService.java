package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.zxylearn.entity.MessageChat;
import top.zxylearn.entity.MessageSession;
import top.zxylearn.mapper.MessageChatMapper;
import top.zxylearn.mapper.MessageSessionMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
public class MessageChatService {

    private final MessageChatMapper messageChatMapper;
    private final MessageSessionMapper messageSessionMapper;

    public MessageChatService(MessageChatMapper messageChatMapper,
                              MessageSessionMapper messageSessionMapper) {
        this.messageChatMapper = messageChatMapper;
        this.messageSessionMapper = messageSessionMapper;
    }

    public void saveChatMessage(String senderIdValue, String receiverIdValue, String content) {
        Long senderId = parseUserId(senderIdValue, "发送者ID");
        Long receiverId = parseUserId(receiverIdValue, "接收者ID");
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("不能给自己发送消息");
        }
        LocalDateTime now = LocalDateTime.now();

        MessageChat chat = new MessageChat();
        chat.setSenderId(senderId);
        chat.setReceiverId(receiverId);
        chat.setContent(content);
        chat.setCreateTime(now);
        messageChatMapper.insert(chat);
        log.info("聊天消息已落库 messageId={}, senderId={}, receiverId={}", chat.getId(), senderId, receiverId);

        updateSession(senderId, receiverId, chat.getId(), content, now);
    }

    private void updateSession(Long senderId, Long receiverId, Long messageId, String content, LocalDateTime messageTime) {
        Long smallerUserId = Math.min(senderId, receiverId);
        Long largerUserId = Math.max(senderId, receiverId);
        MessageSession session = messageSessionMapper.selectOne(
                new LambdaQueryWrapper<MessageSession>()
                        .eq(MessageSession::getSmallerUserId, smallerUserId)
                        .eq(MessageSession::getLargerUserId, largerUserId)
                        .last("limit 1")
        );
        if (session == null) {
            insertSession(senderId, receiverId, messageId, content, messageTime, smallerUserId, largerUserId);
            return;
        }
        session.setLastMessageId(messageId);
        session.setLastMessageContent(content);
        session.setLastMessageTime(messageTime);
        session.setSmallerUserShow(1);
        session.setLargerUserShow(1);
        session.setUpdateTime(LocalDateTime.now());
        increaseUnreadCount(session, receiverId);
        messageSessionMapper.updateById(session);
    }

    private void insertSession(Long senderId, Long receiverId, Long messageId, String content,
                               LocalDateTime messageTime, Long smallerUserId, Long largerUserId) {
        MessageSession session = new MessageSession();
        session.setSmallerUserId(smallerUserId);
        session.setLargerUserId(largerUserId);
        session.setLastMessageId(messageId);
        session.setLastMessageContent(content);
        session.setLastMessageTime(messageTime);
        session.setSmallerUserUnreadCount(0L);
        session.setLargerUserUnreadCount(0L);
        session.setSmallerUserShow(1);
        session.setLargerUserShow(1);
        increaseUnreadCount(session, receiverId);
        try {
            messageSessionMapper.insert(session);
        } catch (DuplicateKeyException ex) {
            updateSession(senderId, receiverId, messageId, content, messageTime);
        }
    }

    private void increaseUnreadCount(MessageSession session, Long receiverId) {
        if (receiverId.equals(session.getSmallerUserId())) {
            session.setSmallerUserUnreadCount(nullToZero(session.getSmallerUserUnreadCount()) + 1);
        } else {
            session.setLargerUserUnreadCount(nullToZero(session.getLargerUserUnreadCount()) + 1);
        }
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private Long parseUserId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + "格式错误");
        }
    }
}
