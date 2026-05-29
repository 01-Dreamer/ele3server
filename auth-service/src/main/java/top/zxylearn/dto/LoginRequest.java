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
}
