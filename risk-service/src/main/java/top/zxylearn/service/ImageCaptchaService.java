package top.zxylearn.service;

import cloud.tianai.captcha.application.ImageCaptchaApplication;
import cloud.tianai.captcha.application.vo.ImageCaptchaVO;
import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.common.response.ApiResponse;
import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import org.springframework.stereotype.Service;

@Service
public class ImageCaptchaService {

    private final ImageCaptchaApplication imageCaptchaApplication;

    public ImageCaptchaService(ImageCaptchaApplication imageCaptchaApplication) {
        this.imageCaptchaApplication = imageCaptchaApplication;
    }

    public ApiResponse<ImageCaptchaVO> generateSliderCaptcha() {
        return imageCaptchaApplication.generateCaptcha(CaptchaTypeConstant.SLIDER);
    }

    public ApiResponse<?> verifySliderCaptcha(String id, ImageCaptchaTrack data) {
        return imageCaptchaApplication.matching(id, data);
    }
}
