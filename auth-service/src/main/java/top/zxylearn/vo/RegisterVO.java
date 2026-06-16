package top.zxylearn.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "注册结果")
public class RegisterVO {

    @Schema(description = "用户 ID，字符串格式避免前端精度丢失")
    private String userId;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "状态：0正常，1封号")
    private Integer status;
}
