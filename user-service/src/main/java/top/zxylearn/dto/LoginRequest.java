package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "用户登录请求")
public class LoginRequest {

    @Schema(description = "邮箱", example = "1234567890@qq.com")
    private String email;

    @Schema(description = "密码", example = "abc123456")
    private String password;

    @Schema(description = "滑块验证码 ID")
    private String sliderCaptchaId;

    @Schema(description = "前端滑动轨迹数据")
    private Map<String, Object> sliderCaptchaData;
}
