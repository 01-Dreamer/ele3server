package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户资料修改请求")
public class UserUpdateRequest {

    @Schema(description = "昵称", example = "小明")
    private String nickname;

    @Schema(description = "头像 URL", example = "https://example.com/avatar.png")
    private String avatarUrl;

    @Schema(description = "性别：0未知，1男，2女", example = "1")
    private Integer gender;
}
