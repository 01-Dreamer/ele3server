package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

@Tag(name = "管理员接口")
@RestController
@RequestMapping("/api/message/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final MessageSessionService messageSessionService;
    private final MessageChatService messageChatService;
    private final MessageNoticeService messageNoticeService;

    public AdminController(MessageSessionService messageSessionService,
                           MessageChatService messageChatService,
                           MessageNoticeService messageNoticeService) {
        this.messageSessionService = messageSessionService;
        this.messageChatService = messageChatService;
        this.messageNoticeService = messageNoticeService;
    }

    @Operation(summary = "管理员查询用户会话列表（游标分页）")
    @GetMapping("/list-session")
    public Result<CursorPageVO<MessageSessionVO>> listSessions(
            @RequestParam("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageSessionService.listSessionsByAdmin(userId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员查询会话列表失败 userId={}", userId, ex);
            return Result.fail(500, "会话列表查询失败");
        }
    }

    @Operation(summary = "管理员查询两人聊天记录（游标分页）")
    @GetMapping("/list-chat")
    public Result<CursorPageVO<MessageChatVO>> listChatHistory(
            @RequestParam("userA") String userA,
            @RequestParam("userB") String userB,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageChatService.listChatHistoryByAdmin(userA, userB, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员查询聊天记录失败 userA={}, userB={}", userA, userB, ex);
            return Result.fail(500, "聊天记录查询失败");
        }
    }

    @Operation(summary = "管理员查询用户通知列表（游标分页）")
    @GetMapping("/list-notice")
    public Result<CursorPageVO<MessageNoticeVO>> listNotices(
            @RequestParam("userId") String userId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", required = false) Integer size) {
        try {
            return Result.success(messageNoticeService.listNoticesByAdmin(userId, cursor, size));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员查询通知列表失败 userId={}", userId, ex);
            return Result.fail(500, "通知列表查询失败");
        }
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/delete-notice/{noticeId}")
    public Result<?> deleteNotice(@PathVariable String noticeId) {
        try {
            messageNoticeService.deleteNoticeByAdmin(noticeId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员删除通知失败 noticeId={}", noticeId, ex);
            return Result.fail(500, "通知删除失败");
        }
    }

    @Operation(summary = "删除聊天消息")
    @DeleteMapping("/delete-chat/{chatId}")
    public Result<?> deleteChat(@PathVariable String chatId) {
        try {
            messageChatService.deleteChatByAdmin(chatId);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            log.error("管理员删除聊天消息失败 chatId={}", chatId, ex);
            return Result.fail(500, "聊天消息删除失败");
        }
    }
}
