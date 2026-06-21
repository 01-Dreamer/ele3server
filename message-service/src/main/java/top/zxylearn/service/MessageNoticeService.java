package top.zxylearn.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.zxylearn.entity.MessageNotice;
import top.zxylearn.mapper.MessageNoticeMapper;
import top.zxylearn.service.CursorPageHelper.CursorParams;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.MessageNoticeVO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageNoticeService {

    private static final int UNREAD = 0;

    private final MessageNoticeMapper messageNoticeMapper;

    public MessageNoticeService(MessageNoticeMapper messageNoticeMapper) {
        this.messageNoticeMapper = messageNoticeMapper;
    }

    @Async
    public void saveSystemNoticeAsync(String receiverId, JsonNode data) {
        try {
            saveSystemNotice(receiverId, data);
        } catch (RuntimeException ex) {
            log.warn("系统消息异步保存失败 receiverId={}", receiverId, ex);
        }
    }

    public void saveSystemNotice(String receiverIdValue, JsonNode data) {
        Long receiverId = parseUserId(receiverIdValue);
        String title = extractTitle(data);
        String content = extractContent(data);
        if (!hasText(title)) {
            throw new IllegalArgumentException("系统消息标题不能为空");
        }
        if (!hasText(content)) {
            throw new IllegalArgumentException("系统消息内容不能为空");
        }
        MessageNotice notice = new MessageNotice();
        notice.setUserId(receiverId);
        notice.setTitle(title.trim());
        notice.setContent(content.trim());
        notice.setIsRead(UNREAD);
        notice.setCreateTime(LocalDateTime.now());
        messageNoticeMapper.insert(notice);
        log.info("系统消息已保存 noticeId={}, receiverId={}", notice.getId(), receiverId);
    }

    private String extractTitle(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        JsonNode titleNode = data.get("title");
        return titleNode != null && titleNode.isTextual() ? titleNode.asText() : null;
    }

    private String extractContent(JsonNode data) {
        if (data == null || data.isNull()) {
            return null;
        }
        JsonNode contentNode = data.get("content");
        return contentNode != null && contentNode.isTextual() ? contentNode.asText() : null;
    }

    private Long parseUserId(String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("接收者ID不能为空");
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("接收者ID格式错误");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // ======================== 用户操作 ========================

    public void markAsRead(String userId, String noticeId) {
        Long userLong = parseUserId(userId);
        Long noticeIdLong = parseUserId(noticeId);
        MessageNotice notice = messageNoticeMapper.selectById(noticeIdLong);
        if (notice == null) {
            throw new IllegalArgumentException("通知不存在");
        }
        if (!userLong.equals(notice.getUserId())) {
            throw new IllegalArgumentException("无权操作该通知");
        }
        if (notice.getIsRead() != null && notice.getIsRead() == 1) {
            return; // 幂等：已读无需再更新
        }
        LambdaUpdateWrapper<MessageNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageNotice::getId, noticeIdLong)
                .set(MessageNotice::getIsRead, 1)
                .set(MessageNotice::getUpdateTime, LocalDateTime.now());
        messageNoticeMapper.update(null, wrapper);
        log.info("通知已标记为已读 userId={}, noticeId={}", userId, noticeId);
    }

    // ======================== 用户/管理员 游标分页 ========================

    public CursorPageVO<MessageNoticeVO> listUserNotices(String userId, String cursor, Integer size) {
        Long userLong = parseUserId(userId);
        return listNotices(userLong, cursor, size);
    }

    public CursorPageVO<MessageNoticeVO> listNoticesByAdmin(String targetUserId, String cursor, Integer size) {
        Long userLong = parseUserId(targetUserId);
        return listNotices(userLong, cursor, size);
    }

    private CursorPageVO<MessageNoticeVO> listNotices(Long userLong, String cursor, Integer size) {
        CursorParams cp = CursorPageHelper.parseCursor(cursor);
        int pageSize = CursorPageHelper.normalizeSize(size);

        LambdaQueryWrapper<MessageNotice> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageNotice::getUserId, userLong);
        applyCursor(wrapper, cp);
        wrapper.orderByDesc(MessageNotice::getUpdateTime)
                .orderByDesc(MessageNotice::getId)
                .last("LIMIT " + (pageSize + 1));

        return buildResult(messageNoticeMapper.selectList(wrapper), pageSize);
    }

    public void markAllAsRead(String userId) {
        Long userLong = parseUserId(userId);
        LambdaUpdateWrapper<MessageNotice> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MessageNotice::getUserId, userLong)
                .eq(MessageNotice::getIsRead, 0)
                .set(MessageNotice::getIsRead, 1)
                .set(MessageNotice::getUpdateTime, LocalDateTime.now());
        messageNoticeMapper.update(null, wrapper);
        log.info("全部通知已标记为已读 userId={}", userId);
    }

    // ======================== 管理员删除 ========================

    public void deleteNoticeByAdmin(String noticeId) {
        Long noticeIdLong = parseUserId(noticeId);
        int deleted = messageNoticeMapper.deleteById(noticeIdLong);
        if (deleted <= 0) {
            throw new IllegalArgumentException("通知不存在");
        }
        log.info("管理员删除通知 noticeId={}", noticeId);
    }

    // ======================== 内部工具 ========================

    private void applyCursor(LambdaQueryWrapper<MessageNotice> wrapper, CursorParams cp) {
        if (cp.cursorTimeMillis() == null) {
            return;
        }
        LocalDateTime cursorTime = CursorPageHelper.toLocalDateTime(cp.cursorTimeMillis());
        Long cursorId = cp.cursorId();
        wrapper.apply("(update_time < {0} OR (update_time = {0} AND id < {1}))", cursorTime, cursorId);
    }

    private CursorPageVO<MessageNoticeVO> buildResult(List<MessageNotice> list, int pageSize) {
        boolean hasMore = list.size() > pageSize;
        if (hasMore) {
            list = new ArrayList<>(list.subList(0, pageSize));
        }
        List<MessageNoticeVO> items = list.stream()
                .map(this::toNoticeVO)
                .collect(Collectors.toList());
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            MessageNotice last = list.get(list.size() - 1);
            nextCursor = CursorPageHelper.buildNextCursor(last.getUpdateTime(), last.getId());
        }
        return new CursorPageVO<>(items, nextCursor, hasMore);
    }

    private MessageNoticeVO toNoticeVO(MessageNotice n) {
        return new MessageNoticeVO(
                String.valueOf(n.getId()),
                String.valueOf(n.getUserId()),
                n.getTitle(),
                n.getContent(),
                n.getIsRead(),
                n.getCreateTime(),
                n.getUpdateTime()
        );
    }
}
