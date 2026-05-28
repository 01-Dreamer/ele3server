package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "注册邮箱验证码发送请求")
public class RegisterEmailCaptchaSendRequest {

    @Schema(description = "接收验证码的邮箱", example = "1234567890@qq.com")
    private String email;

    @Schema(description = "滑块验证码 ID")
    private String sliderCaptchaId;

    @Schema(description = "前端滑动轨迹数据")
    private Map<String, Object> sliderCaptchaData;
}
