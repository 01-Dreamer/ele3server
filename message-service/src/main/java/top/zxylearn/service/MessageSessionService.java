package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.entity.MessageSession;
import top.zxylearn.mapper.MessageSessionMapper;
import top.zxylearn.service.CursorPageHelper.CursorParams;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.MessageSessionVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageSessionService {

    private static final Logger log = LoggerFactory.getLogger(MessageSessionService.class);

    private final MessageSessionMapper messageSessionMapper;

    public MessageSessionService(MessageSessionMapper messageSessionMapper) {
        this.messageSessionMapper = messageSessionMapper;
    }

    // ======================== 用户操作 ========================

    public void clearUnread(String userId, String sessionId) {
        Long userLong = parseLong(userId, "用户ID");
        Long sessionIdLong = parseLong(sessionId, "会话ID");
        MessageSession session = getSession(sessionIdLong);
        boolean isSmaller = checkOwnership(session, userLong);

        LambdaUpdateWrapper<MessageSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageSession::getId, sessionIdLong);
        if (isSmaller) {
            wrapper.set(MessageSession::getSmallerUserUnreadCount, 0L);
        } else {
            wrapper.set(MessageSession::getLargerUserUnreadCount, 0L);
        }
        messageSessionMapper.update(null, wrapper);
        log.info("会话未读已清空 userId={}, sessionId={}", userId, sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void hideSession(String userId, String sessionId) {
        Long userLong = parseLong(userId, "用户ID");
        Long sessionIdLong = parseLong(sessionId, "会话ID");
        MessageSession session = getSession(sessionIdLong);
        boolean isSmaller = checkOwnership(session, userLong);

        LambdaUpdateWrapper<MessageSession> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageSession::getId, sessionIdLong);
        if (isSmaller) {
            wrapper.set(MessageSession::getSmallerUserShow, 0);
        } else {
            wrapper.set(MessageSession::getLargerUserShow, 0);
        }
        messageSessionMapper.update(null, wrapper);
        log.info("会话已隐藏 userId={}, sessionId={}", userId, sessionId);

        // 重新查询，若双方都已隐藏则物理删除
        MessageSession updated = messageSessionMapper.selectById(sessionIdLong);
        if (updated.getSmallerUserShow() == 0 && updated.getLargerUserShow() == 0) {
            messageSessionMapper.deleteById(sessionIdLong);
            log.info("双方均已隐藏，会话已删除 sessionId={}", sessionId);
        }
    }

    // ======================== 用户游标分页 ========================

    public CursorPageVO<MessageSessionVO> listUserSessions(String userId, String cursor, Integer size) {
        Long userLong = parseLong(userId, "用户ID");
        CursorParams cp = CursorPageHelper.parseCursor(cursor);
        int pageSize = CursorPageHelper.normalizeSize(size);

        LambdaQueryWrapper<MessageSession> wrapper = new LambdaQueryWrapper<>();
        // 用户在 smaller 侧且 show=1，或在 larger 侧且 show=1
        wrapper.and(w -> w
                .and(s -> s.eq(MessageSession::getSmallerUserId, userLong)
                        .eq(MessageSession::getSmallerUserShow, 1))
                .or(l -> l.eq(MessageSession::getLargerUserId, userLong)
                        .eq(MessageSession::getLargerUserShow, 1))
        );
        applyCursor(wrapper, cp, "update_time");
        wrapper.orderByDesc(MessageSession::getUpdateTime)
                .orderByDesc(MessageSession::getId)
                .last("LIMIT " + (pageSize + 1));

        return buildResult(messageSessionMapper.selectList(wrapper), pageSize);
    }

    // ======================== 管理员操作 ========================

    public CursorPageVO<MessageSessionVO> listSessionsByAdmin(String targetUserId, String cursor, Integer size) {
        Long userLong = parseLong(targetUserId, "用户ID");
        CursorParams cp = CursorPageHelper.parseCursor(cursor);
        int pageSize = CursorPageHelper.normalizeSize(size);

        LambdaQueryWrapper<MessageSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(MessageSession::getSmallerUserId, userLong)
                .or().eq(MessageSession::getLargerUserId, userLong));
        applyCursor(wrapper, cp, "update_time");
        wrapper.orderByDesc(MessageSession::getUpdateTime)
                .orderByDesc(MessageSession::getId)
                .last("LIMIT " + (pageSize + 1));

        return buildResult(messageSessionMapper.selectList(wrapper), pageSize);
    }

    // ======================== 内部工具 ========================

    private MessageSession getSession(Long sessionId) {
        MessageSession session = messageSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        return session;
    }

    /**
     * 检查用户是否属于该会话。返回 true 表示是 smaller 侧，false 表示 larger 侧。
     * 若用户不属于该会话则抛出异常。
     */
    private boolean checkOwnership(MessageSession session, Long userId) {
        if (userId.equals(session.getSmallerUserId())) {
            return true;
        }
        if (userId.equals(session.getLargerUserId())) {
            return false;
        }
        throw new IllegalArgumentException("无权操作该会话");
    }

    @SuppressWarnings("unchecked")
    private <T> void applyCursor(LambdaQueryWrapper<T> wrapper, CursorParams cp, String timeColumn) {
        if (cp.cursorTimeMillis() == null) {
            return;
        }
        LocalDateTime cursorTime = CursorPageHelper.toLocalDateTime(cp.cursorTimeMillis());
        Long cursorId = cp.cursorId();
        wrapper.apply("(" + timeColumn + " < {0} OR (" + timeColumn + " = {0} AND id < {1}))", cursorTime, cursorId);
    }

    private CursorPageVO<MessageSessionVO> buildResult(List<MessageSession> list, int pageSize) {
        boolean hasMore = list.size() > pageSize;
        if (hasMore) {
            list = new ArrayList<>(list.subList(0, pageSize));
        }
        List<MessageSessionVO> items = list.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            MessageSession last = list.get(list.size() - 1);
            nextCursor = CursorPageHelper.buildNextCursor(last.getUpdateTime(), last.getId());
        }
        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    private MessageSessionVO toVO(MessageSession s) {
        return new MessageSessionVO(
                String.valueOf(s.getId()),
                String.valueOf(s.getSmallerUserId()),
                String.valueOf(s.getLargerUserId()),
                s.getLastMessageId() != null ? String.valueOf(s.getLastMessageId()) : null,
                s.getLastMessageContent(),
                s.getLastMessageTime(),
                s.getSmallerUserUnreadCount(),
                s.getLargerUserUnreadCount(),
                s.getSmallerUserShow(),
                s.getLargerUserShow(),
                s.getCreateTime(),
                s.getUpdateTime()
        );
    }

    private Long parseLong(String value, String fieldName) {
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
