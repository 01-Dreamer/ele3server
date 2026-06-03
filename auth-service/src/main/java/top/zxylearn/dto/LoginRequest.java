package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "邮箱", example = "test@qq.com")
    private String email;

    @Schema(description = "密码", example = "abc123456")
    private String password;

    @Schema(description = "图片验证码 ID")
    private String captchaId;

    @Schema(description = "图片验证码答案", example = "aB3K9q")
    private String captchaCode;
}
