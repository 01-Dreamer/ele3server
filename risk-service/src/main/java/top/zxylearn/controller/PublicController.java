package top.zxylearn.controller;

import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.zxylearn.result.Result;
import top.zxylearn.service.ImageCaptchaService;
import top.zxylearn.vo.TextCaptchaVO;

@Tag(name = "验证码公共接口")
@RestController
@RequestMapping("/api/risk/public/captcha")
public class PublicController {

    private final ImageCaptchaService imageCaptchaService;

    public PublicController(ImageCaptchaService imageCaptchaService) {
        this.imageCaptchaService = imageCaptchaService;
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
}
