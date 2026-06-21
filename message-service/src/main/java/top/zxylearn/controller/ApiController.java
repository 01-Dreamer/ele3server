package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.MessageChatService;
import top.zxylearn.service.MessageNoticeService;
import top.zxylearn.service.MessageSessionService;
import top.zxylearn.vo.CursorPageVO;
import top.zxylearn.vo.MessageChatVO;
import top.zxylearn.vo.MessageNoticeVO;
import top.zxylearn.vo.MessageSessionVO;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/api/message")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final MessageChatService messageChatService;
    private final MessageNoticeService messageNoticeService;
    private final MessageSessionService messageSessionService;

    public ApiController(MessageChatService messageChatService,
                         MessageNoticeService messageNoticeService,
                         MessageSessionService messageSessionService) {
        this.messageChatService = messageChatService;
        this.messageNoticeService = messageNoticeService;
        this.messageSessionService = messageSessionService;
    }

    @Operation(summary = "标记通知为已读")
    @PutMapping("/read-notice/{noticeId}")
    public Result<?> readNotice(@RequestHeader("X-User-Id") String userId,
                                @PathVariable String noticeId) {
        try {
            messageNoticeService.markAsRead(userId, noticeId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("通知标记已读失败 userId={}, noticeId={}", userId, noticeId, ex);
            return Result.fail(500, "通知标记已读失败");
        }
    }

    @Operation(summary = "清空会话未读计数")
    @PutMapping("/clear-unread/{sessionId}")
    public Result<?> clearUnread(@RequestHeader("X-User-Id") String userId,
                                 @PathVariable String sessionId) {
        try {
            messageSessionService.clearUnread(userId, sessionId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("会话未读清空失败 userId={}, sessionId={}", userId, sessionId, ex);
            return Result.fail(500, "会话未读清空失败");
        }
    }

    @Operation(summary = "隐藏会话（双方都隐藏后自动删除）")
    @DeleteMapping("/hide-session/{sessionId}")
    public Result<?> hideSession(@RequestHeader("X-User-Id") String userId,
                                 @PathVariable String sessionId) {
        try {
            messageSessionService.hideSession(userId, sessionId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("会话隐藏失败 userId={}, sessionId={}", userId, sessionId, ex);
            return Result.fail(500, "会话隐藏失败");
        }
    }

    @Operation(summary = "获取自己的会话列表（游标分页）")
    @GetMapping("/list-session")
    public Result<CursorPageVO<MessageSessionVO>> listSessions(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageSessionService.listUserSessions(userId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("会话列表获取失败 userId={}", userId, ex);
            return Result.fail(500, "会话列表获取失败");
        }
    }

    @Operation(summary = "获取自己的聊天消息列表（游标分页）")
    @GetMapping("/list-chat")
    public Result<CursorPageVO<MessageChatVO>> listChats(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageChatService.listUserChats(userId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("聊天消息列表获取失败 userId={}", userId, ex);
            return Result.fail(500, "聊天消息列表获取失败");
        }
    }

    @Operation(summary = "全部通知标记为已读")
    @PutMapping("/read-all-notice")
    public Result<?> readAllNotice(@RequestHeader("X-User-Id") String userId) {
        try {
            messageNoticeService.markAllAsRead(userId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("全部通知标记已读失败 userId={}", userId, ex);
            return Result.fail(500, "全部通知标记已读失败");
        }
    }

    @Operation(summary = "获取自己的通知列表（游标分页）")
    @GetMapping("/list-notice")
    public Result<CursorPageVO<MessageNoticeVO>> listNotices(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageNoticeService.listUserNotices(userId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("通知列表获取失败 userId={}", userId, ex);
            return Result.fail(500, "通知列表获取失败");
        }
    }
}
