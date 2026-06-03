package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "忘记密码重置请求")
public class ForgotPasswordResetRequest {

    @Schema(description = "邮箱", example = "test@qq.com")
    private String email;

    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCaptcha;

    @Schema(description = "新密码，只能包含字母和数字，长度 6-20 位", example = "newabc123")
    private String newPassword;
}
