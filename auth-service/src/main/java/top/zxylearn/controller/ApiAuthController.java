package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.EmailCaptchaSendRequest;
import top.zxylearn.dto.LoginRequest;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.EmailCaptchaService;
import top.zxylearn.service.LoginService;
import top.zxylearn.service.RegisterService;
import top.zxylearn.vo.LoginVO;
import top.zxylearn.vo.RegisterVO;

import java.util.Collections;
import java.util.Map;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class ApiAuthController {

    private final EmailCaptchaService emailCaptchaService;
    private final RegisterService registerService;
    private final LoginService loginService;

    public ApiAuthController(EmailCaptchaService emailCaptchaService,
                             RegisterService registerService,
                             LoginService loginService) {
        this.emailCaptchaService = emailCaptchaService;
        this.registerService = registerService;
        this.loginService = loginService;
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
            return Result.success(loginService.login(request, getClientIp(httpServletRequest)));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "登录失败");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
