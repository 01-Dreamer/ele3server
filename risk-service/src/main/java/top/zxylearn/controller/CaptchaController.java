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

@Tag(name = "图形验证码")
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final ImageCaptchaService imageCaptchaService;

    public CaptchaController(ImageCaptchaService imageCaptchaService) {
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
}
