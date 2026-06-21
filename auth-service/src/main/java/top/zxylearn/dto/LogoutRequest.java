package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登出请求")
public class LogoutRequest {

    @Schema(description = "登录token", example = "abc123def456")
    private String token;
}
