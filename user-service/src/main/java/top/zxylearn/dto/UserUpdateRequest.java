package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改用户资料请求")
public class UserUpdateRequest {

    @Schema(description = "昵称，空值表示不修改", example = "小饿")
    private String nickname;

    @Schema(description = "头像URL，空值表示不修改")
    private String avatar;
}
