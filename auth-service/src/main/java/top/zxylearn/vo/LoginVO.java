package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录结果")
public class LoginVO {

    @Schema(description = "登录 token")
    private String token;

    @Schema(description = "用户基本信息")
    private LoginUserVO userInfo;
}
