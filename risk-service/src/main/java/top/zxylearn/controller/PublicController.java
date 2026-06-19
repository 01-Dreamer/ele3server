package top.zxylearn.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.dto.RiskClearBySliderRequest;
import top.zxylearn.result.Result;
import top.zxylearn.service.ImageCaptchaService;
import top.zxylearn.service.RiskScoreService;
import top.zxylearn.vo.TextCaptchaVO;

@Tag(name = "公共接口")
@RestController
@RequestMapping("/api/risk/public/captcha")
public class PublicController {

    private final ImageCaptchaService imageCaptchaService;
    private final RiskScoreService riskScoreService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PublicController(ImageCaptchaService imageCaptchaService, RiskScoreService riskScoreService) {
        this.imageCaptchaService = imageCaptchaService;
        this.riskScoreService = riskScoreService;
    }

    @Operation(summary = "获取滑块验证码")
    @PostMapping("/slider")
    public Result<ImageCaptchaVO> generateSliderCaptcha() {
        try {
            ApiResponse<ImageCaptchaVO> response = imageCaptchaService.generateSliderCaptcha();
            if (response.isSuccess()) {
                return Result.success(response.getData());
            }
            return Result.fail(400, response.getMsg());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage());
        }
    }

    @Operation(summary = "获取图片验证码")
    @PostMapping("/image")
    public Result<TextCaptchaVO> generateTextCaptcha() {
        try {
            return Result.success(imageCaptchaService.generateTextCaptcha());
        } catch (RuntimeException ex) {
            return Result.fail(500, ex.getMessage());
        }
    }
    @Operation(summary = "通过滑块验证码清空用户风险分")
    @PostMapping("/clear-risk-by-slider")
    public Result<?> clearRiskBySlider(@RequestBody RiskClearBySliderRequest request) {
        try {
            checkClearRiskRequest(request);
            ImageCaptchaTrack track = objectMapper.convertValue(request.getCaptchaData(), ImageCaptchaTrack.class);
            ApiResponse<?> response = imageCaptchaService.verifySliderCaptcha(request.getCaptchaId(), track);
            if (!response.isSuccess()) {
                return Result.fail(400, translateCaptchaMessage(response.getMsg()));
            }
            riskScoreService.clearRiskScore(request.getUserId());
            return Result.success();
        } catch (IllegalArgumentException ex) {
            return Result.fail(400, translateCaptchaMessage(ex.getMessage()));
        } catch (RuntimeException ex) {
            return Result.fail(500, "用户风险分清理失败");
        }
    }

    private void checkClearRiskRequest(RiskClearBySliderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request.getCaptchaId() == null || request.getCaptchaId().isBlank()) {
            throw new IllegalArgumentException("验证码 ID 不能为空");
        }
        if (request.getCaptchaData() == null || request.getCaptchaData().isEmpty()) {
            throw new IllegalArgumentException("滑动轨迹不能为空");
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
