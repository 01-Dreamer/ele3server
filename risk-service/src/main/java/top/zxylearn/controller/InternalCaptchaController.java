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
import top.zxylearn.dto.SliderCaptchaVerifyRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ImageCaptchaService;

import java.util.Collections;

@Tag(name = "内部验证码")
@RestController
@RequestMapping("/internal/risk/captcha")
public class InternalCaptchaController {

    private final ImageCaptchaService imageCaptchaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public InternalCaptchaController(ImageCaptchaService imageCaptchaService) {
        this.imageCaptchaService = imageCaptchaService;
    }

    @Operation(summary = "校验滑块验证码")
    @PostMapping("/slider/verify")
    public Result<?> verifySliderCaptcha(@RequestBody SliderCaptchaVerifyRequest request) {
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
