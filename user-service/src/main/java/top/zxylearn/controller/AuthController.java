package top.zxylearn.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.LoginRequest;
import top.zxylearn.dto.RegisterEmailCaptchaSendRequest;
import top.zxylearn.dto.RegisterRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.LoginService;
import top.zxylearn.service.RegisterService;

import java.util.Collections;

@Tag(name = "认证模块")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegisterService registerService;
    private final LoginService loginService;

    public AuthController(RegisterService registerService, LoginService loginService) {
        this.registerService = registerService;
        this.loginService = loginService;
    }

    @Operation(summary = "获取注册邮箱验证码")
    @PostMapping("/email-code")
    public Result<?> sendRegisterEmailCode(@RequestBody RegisterEmailCaptchaSendRequest request) {
        try {
            registerService.sendRegisterEmailCode(request);
            return Result.success(Collections.singletonMap("email", request.getEmail()));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "注册邮箱验证码发送失败");
        }
    }

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<?> register(@RequestBody RegisterRequest request) {
        try {
            registerService.register(request);
            return Result.success(Collections.singletonMap("email", request.getEmail()));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户注册失败");
        }
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<?> login(@RequestBody LoginRequest request) {
        try {
            return Result.success(loginService.login(request));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户登录失败");
        }
    }
}
