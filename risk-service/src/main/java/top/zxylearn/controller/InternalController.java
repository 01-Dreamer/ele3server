package top.zxylearn.controller;

import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.CaptchaVerifyRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ImageCaptchaService;

import java.util.Collections;
import java.util.Locale;

@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/risk/captcha")
public class InternalController {

    private final ImageCaptchaService imageCaptchaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalController(ImageCaptchaService imageCaptchaService) {
        this.imageCaptchaService = imageCaptchaService;
    }

    @Operation(summary = "校验验证码")
    @PostMapping("/verify")
    public Result<?> verifyCaptcha(@RequestBody CaptchaVerifyRequest request) {
        if (request == null || request.getId() == null || request.getId().isBlank()) {
            return Result.fail(400, "验证码 ID 不能为空");
        }
        String type = resolveCaptchaType(request);
        if (ImageCaptchaService.TEXT_CAPTCHA_TYPE.equals(type)) {
            return verifyTextCaptcha(request);
        }
        if ("SLIDER".equals(type)) {
            return verifySliderCaptcha(request);
        }
        return Result.fail(400, "验证码类型不支持");
    }

    private Result<?> verifySliderCaptcha(CaptchaVerifyRequest request) {
        if (request == null || request.getId() == null || request.getData() == null) {
            return Result.fail(400, "验证码 ID 和滑动轨迹不能为空");
        }
        try {
            ImageCaptchaTrack track = objectMapper.convertValue(request.getData(), ImageCaptchaTrack.class);
            ApiResponse<?> response = imageCaptchaService.verifySliderCaptcha(request.getId(), track);
            if (response.isSuccess()) {
                return Result.success(Collections.singletonMap("id", request.getId()));
            }
            return Result.fail(400, translateCaptchaMessage(response.getMsg()));
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, translateCaptchaMessage(ex.getMessage()));
        }
    }

    private Result<?> verifyTextCaptcha(CaptchaVerifyRequest request) {
        try {
            if (imageCaptchaService.verifyTextCaptcha(request.getId(), request.getCode())) {
                return Result.success(Collections.singletonMap("id", request.getId()));
            }
            return Result.fail(400, "图片验证码错误或已过期");
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, ex.getMessage());
        }
    }

    private String resolveCaptchaType(CaptchaVerifyRequest request) {
        if (request.getType() != null && !request.getType().isBlank()) {
            return request.getType().trim().toUpperCase(Locale.ROOT);
        }
        if (request.getCode() != null && !request.getCode().isBlank()) {
            return ImageCaptchaService.TEXT_CAPTCHA_TYPE;
        }
        return "SLIDER";
    }

    private String translateCaptchaMessage(String message) {
        if (message == null) {
            return "滑块验证码校验失败";
        }
        return switch (message) {
            case "basic check fail" -> "滑动轨迹校验失败";
            case "trackList must not be null" -> "滑动轨迹不能为空";
            case "bgImageWidth must not be null" -> "背景图宽度不能为空";
            case "bgImageHeight must not be null" -> "背景图高度不能为空";
            case "startSlidingTime must not be null" -> "滑动开始时间不能为空";
            case "endSlidingTime must not be null" -> "滑动结束时间不能为空";
            case "track[x,y,t,type] must not be null" -> "滑动轨迹点数据不完整";
            default -> message;
        };
    }
}
