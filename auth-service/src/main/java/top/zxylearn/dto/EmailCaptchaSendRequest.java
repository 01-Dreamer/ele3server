package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "邮箱验证码发送请求")
public class EmailCaptchaSendRequest {

    @Schema(description = "接收验证码的邮箱", example = "test@qq.com")
    private String email;

    @Schema(description = "滑块验证码 ID")
    private String captchaId;

    @Schema(description = "滑块验证码轨迹数据")
    private Map<String, Object> captchaData;
}
