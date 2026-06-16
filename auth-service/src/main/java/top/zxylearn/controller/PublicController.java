package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.EmailCaptchaSendRequest;
import top.zxylearn.dto.ForgotPasswordResetRequest;
import top.zxylearn.dto.LoginRequest;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.AuthPasswordService;
import top.zxylearn.service.EmailCaptchaService;
import top.zxylearn.service.LoginService;
import top.zxylearn.service.RegisterService;
import top.zxylearn.util.IpUtils;
import top.zxylearn.vo.LoginVO;
import top.zxylearn.vo.RegisterVO;

import java.util.Collections;
import java.util.Map;

@Tag(name = "认证公共接口")
@RestController
@RequestMapping("/api/auth/public")
public class PublicController {

    private final EmailCaptchaService emailCaptchaService;
    private final RegisterService registerService;
    private final LoginService loginService;
    private final AuthPasswordService authPasswordService;

    public PublicController(EmailCaptchaService emailCaptchaService,
                            RegisterService registerService,
                            LoginService loginService,
                            AuthPasswordService authPasswordService) {
        this.emailCaptchaService = emailCaptchaService;
        this.registerService = registerService;
        this.loginService = loginService;
        this.authPasswordService = authPasswordService;
    }

    @Operation(summary = "获取注册邮箱验证码")
    @PostMapping("/register/email-captcha")
    public Result<Map<String, Long>> sendRegisterEmailCaptcha(@RequestBody EmailCaptchaSendRequest request) {
        try {
            long expireSeconds = emailCaptchaService.sendRegisterEmailCaptcha(request);
            return Result.success(Collections.singletonMap("expireSeconds", expireSeconds));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "注册邮箱验证码发送失败");
        }
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<RegisterVO> register(@RequestBody RegisterRequest request) {
        try {
            return Result.success(registerService.register(request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "注册失败");
        }
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        try {
            return Result.success(loginService.login(request, IpUtils.getClientIp(httpServletRequest)));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "登录失败");
        }
    }

    @Operation(summary = "获取忘记密码邮箱验证码")
    @PostMapping("/forgot-password/email-captcha")
    public Result<Map<String, Long>> sendForgotPasswordEmailCaptcha(@RequestBody EmailCaptchaSendRequest request) {
        try {
            long expireSeconds = emailCaptchaService.sendForgotPasswordEmailCaptcha(request);
            return Result.success(Collections.singletonMap("expireSeconds", expireSeconds));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "忘记密码邮箱验证码发送失败");
        }
    }

    @Operation(summary = "忘记密码重置")
    @PutMapping("/forgot-password")
    public Result<?> resetForgotPassword(@RequestBody ForgotPasswordResetRequest request) {
        try {
            authPasswordService.resetForgotPassword(request);
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "密码重置失败");
        }
    }
}
