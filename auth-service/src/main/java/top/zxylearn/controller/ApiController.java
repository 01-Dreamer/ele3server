package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.ChangePasswordRequest;
import top.zxylearn.dto.EmailCaptchaSendRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.AuthPasswordService;

import java.util.Collections;
import java.util.Map;

@Tag(name = "认证用户接口")
@RestController
@RequestMapping("/api/auth")
public class ApiController {

    private final AuthPasswordService authPasswordService;

    public ApiController(AuthPasswordService authPasswordService) {
        this.authPasswordService = authPasswordService;
    }

    @Operation(summary = "修改密码")
    @PutMapping("/change-password")
    public Result<?> changePassword(@RequestHeader("X-User-Id") String userId,
                                    @RequestBody ChangePasswordRequest request) {
        try {
            authPasswordService.changePassword(userId, request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "密码修改失败");
        }
    }

    @Operation(summary = "获取修改密码邮箱验证码")
    @PostMapping("/change-password/email-captcha")
    public Result<Map<String, Long>> sendChangePasswordEmailCaptcha(@RequestHeader("X-User-Id") String userId,
                                                                    @RequestBody EmailCaptchaSendRequest request) {
        try {
            long expireSeconds = authPasswordService.sendChangePasswordEmailCaptcha(userId, request);
            return Result.success(Collections.singletonMap("expireSeconds", expireSeconds));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "修改密码邮箱验证码发送失败");
        }
    }
}
