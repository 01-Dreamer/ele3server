package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @Schema(description = "邮箱", example = "test@qq.com")
    private String email;

    @Schema(description = "密码，只能包含字母和数字，长度 6-20 位", example = "abc123456")
    private String password;

    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCaptcha;
}
