package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "邮箱验证码发送请求")
public class EmailCaptchaSendRequest {

    @Schema(description = "接收验证码的邮箱", example = "1234567890@qq.com")
    private String email;
}
