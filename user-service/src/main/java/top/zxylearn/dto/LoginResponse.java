package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户登录响应")
public class LoginResponse {

    @Schema(description = "登录令牌")
    private String token;

    @Schema(description = "用户 ID")
    private String userId;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像 URL")
    private String avatarUrl;

    @Schema(description = "性别：0未知，1男，2女")
    private Integer gender;
}
