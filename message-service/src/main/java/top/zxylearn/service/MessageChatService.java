package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import top.zxylearn.entity.MessageChat;
import top.zxylearn.entity.MessageSession;
import top.zxylearn.mapper.MessageChatMapper;
import top.zxylearn.mapper.MessageSessionMapper;
import top.zxylearn.service.CursorPageHelper.CursorParams;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.MessageChatVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    // ======================== 用户游标分页 ========================

    public CursorPageVO<MessageChatVO> listUserChats(String userId, String cursor, Integer size) {
        Long userLong = parseUserId(userId, "用户ID");
        CursorParams cp = CursorPageHelper.parseCursor(cursor);
        int pageSize = CursorPageHelper.normalizeSize(size);

        LambdaQueryWrapper<MessageChat> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(MessageChat::getSenderId, userLong)
                .or().eq(MessageChat::getReceiverId, userLong));
        applyCursor(wrapper, cp, "create_time");
        wrapper.orderByDesc(MessageChat::getCreateTime)
                .orderByDesc(MessageChat::getId)
                .last("LIMIT " + (pageSize + 1));

        return buildChatResult(messageChatMapper.selectList(wrapper), pageSize);
    }

    // ======================== 管理员操作 ========================

    public CursorPageVO<MessageChatVO> listChatHistoryByAdmin(String userA, String userB, String cursor, Integer size) {
        Long userALong = parseUserId(userA, "用户A");
        Long userBLong = parseUserId(userB, "用户B");
        Long smaller = Math.min(userALong, userBLong);
        Long larger = Math.max(userALong, userBLong);
        CursorParams cp = CursorPageHelper.parseCursor(cursor);
        int pageSize = CursorPageHelper.normalizeSize(size);

        LambdaQueryWrapper<MessageChat> wrapper = new LambdaQueryWrapper<>();
        // 双向查询：A→B 或 B→A
        wrapper.and(w -> w
                .and(a2b -> a2b.eq(MessageChat::getSenderId, smaller).eq(MessageChat::getReceiverId, larger))
                .or(b2a -> b2a.eq(MessageChat::getSenderId, larger).eq(MessageChat::getReceiverId, smaller))
        );
        applyCursor(wrapper, cp, "create_time");
        wrapper.orderByDesc(MessageChat::getCreateTime)
                .orderByDesc(MessageChat::getId)
                .last("LIMIT " + (pageSize + 1));

        return buildChatResult(messageChatMapper.selectList(wrapper), pageSize);
    }

    public void deleteChatByAdmin(String chatId) {
        Long chatIdLong = parseUserId(chatId, "聊天消息ID");
        int deleted = messageChatMapper.deleteById(chatIdLong);
        if (deleted <= 0) {
            throw new IllegalArgumentException("聊天消息不存在");
        }
        log.info("管理员删除聊天消息 chatId={}", chatId);
    }

    // ======================== 内部工具 ========================

    private void applyCursor(LambdaQueryWrapper<MessageChat> wrapper, CursorParams cp, String timeColumn) {
        if (cp.cursorTimeMillis() == null) {
            return;
        }
        LocalDateTime cursorTime = CursorPageHelper.toLocalDateTime(cp.cursorTimeMillis());
        Long cursorId = cp.cursorId();
        wrapper.apply("(" + timeColumn + " < {0} OR (" + timeColumn + " = {0} AND id < {1}))", cursorTime, cursorId);
    }

    private CursorPageVO<MessageChatVO> buildChatResult(List<MessageChat> list, int pageSize) {
        boolean hasMore = list.size() > pageSize;
        if (hasMore) {
            list = new ArrayList<>(list.subList(0, pageSize));
        }
        List<MessageChatVO> items = list.stream()
                .map(this::toChatVO)
                .collect(Collectors.toList());
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            MessageChat last = list.get(list.size() - 1);
            nextCursor = CursorPageHelper.buildNextCursor(last.getCreateTime(), last.getId());
        }
        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    private MessageChatVO toChatVO(MessageChat c) {
        return new MessageChatVO(
                String.valueOf(c.getId()),
                String.valueOf(c.getSenderId()),
                String.valueOf(c.getReceiverId()),
                c.getContent(),
                c.getCreateTime()
        );
    }
}
