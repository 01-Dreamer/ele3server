package top.zxylearn.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.zxylearn.entity.MessageNotice;
import top.zxylearn.mapper.MessageNoticeMapper;

import java.time.LocalDateTime;

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
}
