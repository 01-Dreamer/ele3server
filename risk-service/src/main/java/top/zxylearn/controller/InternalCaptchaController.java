package top.zxylearn.controller;

import cloud.tianai.captcha.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.EmailCaptchaSendRequest;
import top.zxylearn.dto.EmailCaptchaVerifyRequest;
import top.zxylearn.dto.SliderCaptchaVerifyRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.EmailCaptchaService;
import top.zxylearn.service.ImageCaptchaService;

import java.util.Collections;

@Tag(name = "内部验证码")
@RestController
@RequestMapping("/internal/risk/captcha")
public class InternalCaptchaController {

    private final ImageCaptchaService imageCaptchaService;
    private final EmailCaptchaService emailCaptchaService;

    public InternalCaptchaController(ImageCaptchaService imageCaptchaService, EmailCaptchaService emailCaptchaService) {
        this.imageCaptchaService = imageCaptchaService;
        this.emailCaptchaService = emailCaptchaService;
    }

    @Operation(summary = "校验滑块验证码")
    @PostMapping("/slider/verify")
    public Result<?> verifySliderCaptcha(@RequestBody SliderCaptchaVerifyRequest request) {
        if (request == null || request.getId() == null || request.getData() == null) {
            return Result.fail(400, "captcha id and track data are required");
        }
        ApiResponse<?> response = imageCaptchaService.verifySliderCaptcha(request.getId(), request.getData());
        if (response.isSuccess()) {
            return Result.success(Collections.singletonMap("id", request.getId()));
        }
        return Result.fail(400, response.getMsg());
    }

    @Operation(summary = "获取邮箱验证码")
    @PostMapping("/email")
    public Result<?> sendEmailCaptcha(@RequestBody EmailCaptchaSendRequest request) {
        if (request == null || request.getEmail() == null) {
            return Result.fail(400, "email is required");
        }
        try {
            emailCaptchaService.sendCode(request.getEmail());
            return Result.success(Collections.singletonMap("email", request.getEmail()));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.fail(500, "email captcha send failed");
        }
    }

    @Operation(summary = "校验邮箱验证码")
    @PostMapping("/email/verify")
    public Result<?> verifyEmailCaptcha(@RequestBody EmailCaptchaVerifyRequest request) {
        if (request == null || request.getEmail() == null || request.getCode() == null) {
            return Result.fail(400, "email and code are required");
        }
        try {
            boolean verified = emailCaptchaService.verifyCode(request.getEmail(), request.getCode());
            if (verified) {
                return Result.success(Collections.singletonMap("email", request.getEmail()));
            }
            return Result.fail(400, "email captcha code is invalid");
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
    }
}
