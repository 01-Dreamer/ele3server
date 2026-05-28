package top.zxylearn.dto;

import cloud.tianai.captcha.validator.common.model.dto.ImageCaptchaTrack;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "滑块验证码校验请求")
public class SliderCaptchaVerifyRequest {

    @Schema(description = "验证码 ID")
    private String id;

    @Schema(description = "前端滑动轨迹数据")
    private ImageCaptchaTrack data;
}
