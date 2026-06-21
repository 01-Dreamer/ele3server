package top.zxylearn.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建用户资料请求")
public class UserCreateRequest implements Serializable {

    @Schema(description = "用户ID，雪花ID使用字符串传输", example = "2066777636767580162")
    private String userId;
}
