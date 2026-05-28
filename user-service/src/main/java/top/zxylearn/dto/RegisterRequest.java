package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class RegisterRequest {

    @Schema(description = "邮箱", example = "1234567890@qq.com")
    private String email;

    @Schema(description = "密码，只允许字母或数字，长度 6-20 位", example = "abc123456")
    private String password;

    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCode;
}
