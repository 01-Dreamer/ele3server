package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    @Schema(description = "旧密码", example = "abc123456")
    private String oldPassword;

    @Schema(description = "新密码，只能包含字母和数字，长度 6-20 位", example = "newabc123")
    private String newPassword;
}
