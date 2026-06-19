package top.zxylearn.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "通过滑块验证码清空风险分请求")
public class RiskClearBySliderRequest {

    @Schema(description = "用户ID，雪花ID使用字符串传输")
    private String userId;

    @Schema(description = "滑块验证码ID")
    private String captchaId;

    @Schema(description = "滑块验证码轨迹数据")
    private Map<String, Object> captchaData;
}
